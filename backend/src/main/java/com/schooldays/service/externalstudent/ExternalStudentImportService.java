package com.schooldays.service.externalstudent;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.table;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dto.externalstudent.ExternalStudentListResponse;
import com.schooldays.dto.externalstudent.ExternalStudentImportResponse;
import com.schooldays.dto.externalstudent.ExternalStudentRowResponse;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExternalStudentImportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Table<?> EXTERNAL_STUDENTS = table(name("external_students"));
    private static final Field<Long> SEQ_ID = field(name("seq_id"), Long.class);
    private static final Field<UUID> TENANT_ID = field(name("tenant_id"), UUID.class);
    private static final Field<String> EXTERNAL_ID = field(name("external_id"), String.class);
    private static final Field<String> STUDENT_NAME = field(name("student_name"), String.class);
    private static final Field<String> GENDER = field(name("gender"), String.class);
    private static final Field<LocalDate> BIRTH_DATE = field(name("birth_date"), LocalDate.class);
    private static final Field<JSONB> METADATA = field(name("metadata"), JSONB.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field(name("updated_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> CREATED_AT = field(name("created_at"), OffsetDateTime.class);

    private final DSLContext dsl;
    private final Map<ExternalStudentListCacheKey, ExternalStudentListResponse> studentListCache = new ConcurrentHashMap<>();

    public ExternalStudentImportService(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ExternalStudentImportResponse importStudents(UUID tenantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A CSV or Excel file is required");
        }

        List<Map<String, String>> rows = readRows(file);
        invalidateStudentListCache(tenantId);
        int importedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        try {
            for (int index = 0; index < rows.size(); index++) {
                Map<String, String> row = rows.get(index);
                int rowNumber = index + 2;
                ExternalStudentRow student = ExternalStudentRow.from(row, rowNumber);
                if (!student.isValid()) {
                    skippedCount++;
                    errors.add(student.errorMessage());
                    continue;
                }

                boolean exists = dsl.fetchExists(selectOne()
                        .from(EXTERNAL_STUDENTS)
                        .where(TENANT_ID.eq(tenantId))
                        .and(EXTERNAL_ID.eq(student.externalId())));

                dsl.insertInto(EXTERNAL_STUDENTS)
                        .columns(TENANT_ID, EXTERNAL_ID, STUDENT_NAME, GENDER, BIRTH_DATE, METADATA, CREATED_AT, UPDATED_AT)
                        .values(
                                tenantId,
                                student.externalId(),
                                student.studentName(),
                                student.gender(),
                                student.birthDate(),
                                metadataFor(student),
                                now,
                                now
                        )
                        .onConflict(TENANT_ID, EXTERNAL_ID)
                        .doUpdate()
                        .set(STUDENT_NAME, student.studentName())
                        .set(GENDER, student.gender())
                        .set(BIRTH_DATE, student.birthDate())
                        .set(METADATA, metadataFor(student))
                        .set(UPDATED_AT, now)
                        .execute();

                if (exists) {
                    updatedCount++;
                } else {
                    importedCount++;
                }
            }

            return new ExternalStudentImportResponse(
                    "success",
                    rows.size(),
                    importedCount,
                    updatedCount,
                    skippedCount,
                    errors
            );
        } finally {
            invalidateStudentListCache(tenantId);
        }
    }

    public ExternalStudentListResponse listStudents(UUID tenantId) {
        return studentListCache.computeIfAbsent(
                new ExternalStudentListCacheKey(tenantId, null, null),
                ignored -> fetchStudents(tenantId)
        );
    }

    public ExternalStudentListResponse listStudents(UUID tenantId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        return studentListCache.computeIfAbsent(
                new ExternalStudentListCacheKey(tenantId, safePage, safePageSize),
                ignored -> fetchStudents(tenantId, safePage, safePageSize)
        );
    }

    private ExternalStudentListResponse fetchStudents(UUID tenantId) {
        List<ExternalStudentRowResponse> students = dsl.select(
                        EXTERNAL_ID,
                        STUDENT_NAME,
                        GENDER,
                        BIRTH_DATE,
                        METADATA
                )
                .from(EXTERNAL_STUDENTS)
                .where(TENANT_ID.eq(tenantId))
                .orderBy(SEQ_ID.asc())
                .fetch(record -> {
                    String externalId = record.get(EXTERNAL_ID);
                    String studentName = record.get(STUDENT_NAME);
                    String gender = record.get(GENDER);
                    LocalDate birthDate = record.get(BIRTH_DATE);
                    JSONB metadata = record.get(METADATA);
                    JsonNode metadataNode = readMetadata(metadata);
                    return new ExternalStudentRowResponse(
                            externalId,
                            studentName,
                            metadataText(metadataNode, "lastName"),
                            metadataText(metadataNode, "firstName"),
                            birthDate,
                            metadataText(metadataNode, "gradeLevelCode"),
                            metadataText(metadataNode, "genderCode")
                    );
                });

        long totalRows = students.size();
        return new ExternalStudentListResponse(List.copyOf(students), 1, (int) totalRows, totalRows, 1);
    }

    private ExternalStudentListResponse fetchStudents(UUID tenantId, int safePage, int safePageSize) {
        long totalRows = dsl.selectCount()
                .from(EXTERNAL_STUDENTS)
                .where(TENANT_ID.eq(tenantId))
                .fetchOne(0, long.class);
        int totalPages = (int) Math.max(1, (totalRows + safePageSize - 1) / safePageSize);
        int offset = (safePage - 1) * safePageSize;

        List<ExternalStudentRowResponse> students = dsl.select(
                        EXTERNAL_ID,
                        STUDENT_NAME,
                        GENDER,
                        BIRTH_DATE,
                        METADATA
                )
                .from(EXTERNAL_STUDENTS)
                .where(TENANT_ID.eq(tenantId))
                .orderBy(SEQ_ID.asc())
                .limit(safePageSize)
                .offset(offset)
                .fetch(record -> {
                    String externalId = record.get(EXTERNAL_ID);
                    String studentName = record.get(STUDENT_NAME);
                    String gender = record.get(GENDER);
                    LocalDate birthDate = record.get(BIRTH_DATE);
                    JSONB metadata = record.get(METADATA);
                    JsonNode metadataNode = readMetadata(metadata);
                    return new ExternalStudentRowResponse(
                            externalId,
                            studentName,
                            metadataText(metadataNode, "lastName"),
                            metadataText(metadataNode, "firstName"),
                            birthDate,
                            metadataText(metadataNode, "gradeLevelCode"),
                            metadataText(metadataNode, "genderCode")
                    );
                });
        return new ExternalStudentListResponse(List.copyOf(students), safePage, safePageSize, totalRows, totalPages);
    }

    private void invalidateStudentListCache(UUID tenantId) {
        studentListCache.keySet().removeIf(key -> key.tenantId().equals(tenantId));
    }

    private List<Map<String, String>> readRows(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".csv") || contentType.contains("csv")) {
                return readCsv(file);
            }
            if (filename.endsWith(".xlsx") || filename.endsWith(".xls") || filename.endsWith(".xlsm")
                    || contentType.contains("spreadsheet") || contentType.contains("excel")) {
                return readExcel(file);
            }
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The import file could not be read", exception);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported files are CSV or Excel workbooks");
    }

    private List<Map<String, String>> readCsv(MultipartFile file) throws IOException {
        try (Reader reader = new InputStreamReader(file.getInputStream())) {
            CSVParser parser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build()
                    .parse(reader);
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                parser.getHeaderMap().keySet().forEach(header -> row.put(header, record.get(header)));
                rows.add(row);
            }
            return rows;
        }
    }

    private List<Map<String, String>> readExcel(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            var sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = new ArrayList<>();
            headerRow.forEach(cell -> headers.add(normalizeHeader(formatter.formatCellValue(cell))));

            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean hasValue = false;
                for (int col = 0; col < headers.size(); col++) {
                    String header = headers.get(col);
                    String value = formatter.formatCellValue(row.getCell(col));
                    if (value != null && !value.isBlank()) {
                        hasValue = true;
                    }
                    values.put(header, value);
                }
                if (hasValue) {
                    rows.add(values);
                }
            }
            return rows;
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("The Excel workbook could not be read", exception);
        }
    }

    private JSONB metadataFor(ExternalStudentRow student) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        put(root, "firstName", student.firstName());
        put(root, "lastName", student.lastName());
        put(root, "gradeLevelCode", student.gradeLevelCode());
        put(root, "genderCode", student.genderCode());
        put(root, "source", "import");
        return JSONB.valueOf(root.toString());
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }

    private JsonNode readMetadata(JSONB metadata) {
        try {
            return OBJECT_MAPPER.readTree(metadata == null ? "{}" : metadata.data());
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String metadataText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asText("");
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.trim();
    }

    private record ExternalStudentListCacheKey(UUID tenantId, Integer page, Integer pageSize) {
    }

    private record ExternalStudentRow(
            String externalId,
            String lastName,
            String firstName,
            String studentName,
            LocalDate birthDate,
            String gradeLevelCode,
            String genderCode,
            String gender,
            String errorMessage
    ) {
        static ExternalStudentRow from(Map<String, String> row, int rowNumber) {
            String externalId = value(row, "StudentID");
            String lastName = value(row, "LastName");
            String firstName = value(row, "FirstName");
            String studentName = value(row, "StudentName");
            String birthDateRaw = value(row, "DOB");
            String gradeLevelCode = value(row, "GradeLevelCode");
            String genderCode = value(row, "GenderCode");
            LocalDate birthDate = parseDate(birthDateRaw);
            String gender = genderCode;
            String resolvedName = !studentName.isBlank() ? studentName : buildName(firstName, lastName);

            if (externalId.isBlank()) {
                return new ExternalStudentRow(null, lastName, firstName, resolvedName, birthDate, gradeLevelCode, genderCode, gender, "Row " + rowNumber + ": StudentID is required");
            }
            if (resolvedName.isBlank()) {
                return new ExternalStudentRow(null, lastName, firstName, resolvedName, birthDate, gradeLevelCode, genderCode, gender, "Row " + rowNumber + ": StudentName is required");
            }
            return new ExternalStudentRow(externalId, lastName, firstName, resolvedName, birthDate, gradeLevelCode, genderCode, gender, "");
        }

        boolean isValid() {
            return errorMessage == null || errorMessage.isBlank();
        }

        private static String value(Map<String, String> row, String key) {
            if (row == null) {
                return "";
            }
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(key)) {
                    return entry.getValue() == null ? "" : entry.getValue().trim();
                }
            }
            return "";
        }

        private static String buildName(String firstName, String lastName) {
            return (firstName + " " + lastName).trim();
        }

        private static LocalDate parseDate(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            List<DateTimeFormatter> patterns = List.of(
                    DateTimeFormatter.ISO_LOCAL_DATE,
                    DateTimeFormatter.ofPattern("M/d/uuuu"),
                    DateTimeFormatter.ofPattern("M/d/yy"),
                    DateTimeFormatter.ofPattern("MM/dd/uuuu"),
                    DateTimeFormatter.ofPattern("MM/dd/yy")
            );
            for (DateTimeFormatter formatter : patterns) {
                try {
                    return LocalDate.parse(raw.trim(), formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            try {
                return java.time.LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern("uuuu-M-d"));
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}

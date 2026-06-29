package com.schooldays.dto.program;

import java.util.List;

public record ProgramListResponse(
        List<ProgramResponse> programs
) {
}

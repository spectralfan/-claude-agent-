package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.controller.CodingWorkspaceController;
import com.kama.jchatmind.coding.model.dto.CodingWorkspaceOptionDTO;
import com.kama.jchatmind.coding.service.CodingWorkspaceService;
import com.kama.jchatmind.coding.service.WorkspaceDetectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CodingWorkspaceController.class)
class CodingWorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CodingWorkspaceService codingWorkspaceService;

    @MockitoBean
    private WorkspaceDetectService workspaceDetectService;

    @Test
    void listWorkspaces_shouldReturnOptions() throws Exception {
        when(codingWorkspaceService.listWorkspaceOptions()).thenReturn(List.of(
                CodingWorkspaceOptionDTO.builder()
                        .label("JChatMind 后端")
                        .path("D:/proj/jchatmind")
                        .defaultOption(false)
                        .build(),
                CodingWorkspaceOptionDTO.builder()
                        .label("默认沙箱 (workspace)")
                        .path("D:/sandbox")
                        .defaultOption(true)
                        .build()
        ));

        mockMvc.perform(get("/api/coding/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].label").exists())
                .andExpect(jsonPath("$.data[0].path").exists());
    }
}

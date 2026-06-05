package com.kama.jchatmind.coding;

import com.kama.jchatmind.coding.service.CodingStackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CodingStackServiceTest {

    @Autowired
    private CodingStackService codingStackService;

    @Test
    void listStacks_shouldContainCoreProfiles() {
        var stacks = codingStackService.listStacks();
        assertFalse(stacks.isEmpty());
        assertTrue(stacks.stream().anyMatch(s -> "java-maven".equals(s.getId())));
        assertTrue(stacks.stream().anyMatch(s -> "python-pytest".equals(s.getId())));
        assertTrue(stacks.stream().anyMatch(s -> "node-npm".equals(s.getId())));
    }

    @Test
    void findById_shouldReturnPythonStack() {
        var stack = codingStackService.findById("python-pytest");
        assertTrue(stack.isPresent());
        assertEquals("python", stack.get().getLanguage());
        assertEquals("python-autonomous-dev", stack.get().getSkillId());
    }
}

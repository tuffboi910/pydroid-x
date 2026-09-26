package com.pydroidx.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectWorkspaceTest {
    @Test fun removesUnsafePathCharacters() {
        assertEquals("My Project", ProjectWorkspace.safeName(" ../My/Project? "))
    }

    @Test fun fallsBackForEmptyNames() {
        assertEquals("Project", ProjectWorkspace.safeName("../"))
    }

    @Test fun createsTheNextAvailableProjectName() {
        assertEquals("Project 4", ProjectWorkspace.nextName(listOf("Project", "Project 2", "Project 3")))
    }

    @Test fun preservesAFreeCustomName() {
        assertEquals("Snake Game", ProjectWorkspace.nextName(listOf("Project"), "Snake Game"))
    }
}

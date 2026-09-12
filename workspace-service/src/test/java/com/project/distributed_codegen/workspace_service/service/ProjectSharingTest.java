package com.project.distributed_codegen.workspace_service.service;

import com.project.distributed_codegen.common_lib.dto.UserDto;
import com.project.distributed_codegen.common_lib.enums.ProjectRole;
import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.security.AuthUtil;
import com.project.distributed_codegen.workspace_service.client.AccountClient;
import com.project.distributed_codegen.workspace_service.dto.member.InviteMemberRequest;
import com.project.distributed_codegen.workspace_service.dto.member.UpdateMemberRoleRequest;
import com.project.distributed_codegen.workspace_service.entity.*;
import com.project.distributed_codegen.workspace_service.repository.*;
import com.project.distributed_codegen.workspace_service.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectSharingTest {
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final AuthUtil auth = mock(AuthUtil.class);
    private final AccountClient accounts = mock(AccountClient.class);
    private final ProjectMemberServiceImpl service = new ProjectMemberServiceImpl(members, projects, auth, accounts);

    @Test
    void memberResponseIncludesAccountIdentity() {
        var member = ProjectMember.builder().id(new ProjectMemberId(7L, 2L)).projectRole(ProjectRole.VIEWER).build();
        when(members.findByIdProjectId(7L)).thenReturn(List.of(member));
        when(accounts.getUserById(2L)).thenReturn(new UserDto(2L, "member@example.invalid", "Member"));
        var response = service.getProjectMembers(7L).getFirst();
        assertEquals("Member", response.name()); assertEquals("member@example.invalid", response.username());
        assertEquals(ProjectRole.VIEWER, response.projectRole());
    }

    @Test
    void ownerCannotBeRemovedDemotedOrCreatedThroughSharing() {
        when(auth.getCurrentUserId()).thenReturn(1L);
        var project = Project.builder().id(7L).build();
        when(projects.findAccessibleProjectById(7L, 1L)).thenReturn(Optional.of(project));
        var id = new ProjectMemberId(7L, 1L);
        when(members.findById(id)).thenReturn(Optional.of(ProjectMember.builder().id(id).project(project).projectRole(ProjectRole.OWNER).build()));
        assertThrows(BadRequestException.class, () -> service.removeProjectMember(7L, 1L));
        assertThrows(BadRequestException.class, () -> service.updateMemberRole(7L, 1L, new UpdateMemberRoleRequest(ProjectRole.VIEWER)));
        assertThrows(BadRequestException.class, () -> service.inviteMember(7L, new InviteMemberRequest("member@example.invalid", ProjectRole.OWNER)));
        verify(members, never()).deleteById(any()); verify(members, never()).save(any());
    }
}

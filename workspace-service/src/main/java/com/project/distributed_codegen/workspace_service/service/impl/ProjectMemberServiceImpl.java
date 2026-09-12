package com.project.distributed_codegen.workspace_service.service.impl;

import com.project.distributed_codegen.common_lib.dto.UserDto;
import com.project.distributed_codegen.common_lib.error.BadRequestException;
import com.project.distributed_codegen.common_lib.enums.ProjectRole;
import com.project.distributed_codegen.common_lib.error.ResourceNotFoundException;
import com.project.distributed_codegen.common_lib.security.AuthUtil;
import com.project.distributed_codegen.workspace_service.client.AccountClient;
import com.project.distributed_codegen.workspace_service.dto.member.InviteMemberRequest;
import com.project.distributed_codegen.workspace_service.dto.member.MemberResponse;
import com.project.distributed_codegen.workspace_service.dto.member.UpdateMemberRoleRequest;
import com.project.distributed_codegen.workspace_service.entity.Project;
import com.project.distributed_codegen.workspace_service.entity.ProjectMember;
import com.project.distributed_codegen.workspace_service.entity.ProjectMemberId;
import com.project.distributed_codegen.workspace_service.repository.ProjectMemberRepository;
import com.project.distributed_codegen.workspace_service.repository.ProjectRepository;
import com.project.distributed_codegen.workspace_service.service.ProjectMemberService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Transactional
public class ProjectMemberServiceImpl implements ProjectMemberService {

    ProjectMemberRepository projectMemberRepository;
    ProjectRepository projectRepository;
    AuthUtil authUtil;
    AccountClient accountClient;

    @Override
    @PreAuthorize("@security.canViewMembers(#projectId)")
    public List<MemberResponse> getProjectMembers(Long projectId) {
        return projectMemberRepository.findByIdProjectId(projectId)
                .stream()
                .map(this::memberResponse)
                .toList();
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        requireCollaboratorRole(request.role());
        UserDto invitee = accountClient.getUserByEmail(request.username()).orElseThrow(
                () -> new ResourceNotFoundException("User", request.username())
        );

        if(invitee.id().equals(userId)) {
            throw new BadRequestException("You already own this project");
        }

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, invitee.id());

        if(projectMemberRepository.existsById(projectMemberId)) {
            throw new BadRequestException("This user already has access");
        }

        ProjectMember member = ProjectMember.builder()
                .id(projectMemberId)
                .project(project)
                .projectRole(request.role())
                .invitedAt(Instant.now())
                .build();

        projectMemberRepository.save(member);

        return memberResponse(member);
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public MemberResponse updateMemberRole(Long projectId, Long memberId, UpdateMemberRoleRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        ProjectMember projectMember = projectMemberRepository.findById(projectMemberId).orElseThrow();

        requireCollaboratorRole(request.role());
        protectOwner(projectMember);
        projectMember.setProjectRole(request.role());

        projectMemberRepository.save(projectMember);

        return memberResponse(projectMember);
    }

    @Override
    @PreAuthorize("@security.canManageMembers(#projectId)")
    public void removeProjectMember(Long projectId, Long memberId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        ProjectMemberId projectMemberId = new ProjectMemberId(projectId, memberId);
        ProjectMember member = projectMemberRepository.findById(projectMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Project member", memberId.toString()));
        protectOwner(member);

        projectMemberRepository.deleteById(projectMemberId);
    }

    private MemberResponse memberResponse(ProjectMember member) {
        UserDto user = accountClient.getUserById(member.getId().getUserId());
        return new MemberResponse(user.id(), user.username(), user.name(), member.getProjectRole(), member.getInvitedAt());
    }

    private void requireCollaboratorRole(ProjectRole role) {
        if (role != ProjectRole.EDITOR && role != ProjectRole.VIEWER) {
            throw new BadRequestException("Share access as Editor or Viewer; project ownership cannot be reassigned here");
        }
    }

    private void protectOwner(ProjectMember member) {
        if (member.getProjectRole() == ProjectRole.OWNER) throw new BadRequestException("The project owner cannot be removed or demoted");
    }

    ///  INTERNAL FUNCTIONS

    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId).orElseThrow();
    }
}

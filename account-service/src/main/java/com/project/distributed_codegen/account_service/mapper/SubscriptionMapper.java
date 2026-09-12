package com.project.distributed_codegen.account_service.mapper;

import com.project.distributed_codegen.account_service.dto.subscription.SubscriptionResponse;
import com.project.distributed_codegen.account_service.entity.Plan;
import com.project.distributed_codegen.account_service.entity.Subscription;
import com.project.distributed_codegen.common_lib.dto.PlanDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionResponse toSubscriptionResponse(Subscription subscription);

    PlanDto toPlanResponse(Plan plan);
}

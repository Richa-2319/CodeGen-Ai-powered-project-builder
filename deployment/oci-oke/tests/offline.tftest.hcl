# These are mock-provider runs. They never configure BOAT or call OCI.
mock_provider "oci" {
  mock_data "oci_identity_compartment" {
    defaults = {
      compartment_id = "ocid1.tenancy.oc1..mocktestonly"
      state          = "ACTIVE"
    }
  }
}

variables {
  oci_profile             = "OFFLINE_TEST"
  tenancy_ocid            = "ocid1.tenancy.oc1..mocktestonly"
  parent_compartment_ocid = "ocid1.tenancy.oc1..mocktestonly"
  availability_domain     = "TEST:PHX-AD-1"
  node_image_ocid         = "ocid1.image.oc1.phx.mock-test-only"
  admin_access_cidrs      = ["203.0.113.10/32"]
}

run "isolated_single_worker_plan" {
  command = plan

  assert {
    condition     = oci_containerengine_cluster.demo.type == "BASIC_CLUSTER"
    error_message = "The demo must not provision an enhanced cluster."
  }
  assert {
    condition = (
      oci_containerengine_node_pool.demo.node_config_details[0].size == 1 &&
      oci_containerengine_node_pool.demo.node_shape == "VM.Standard.A1.Flex" &&
      oci_containerengine_node_pool.demo.node_shape_config[0].ocpus == 2 &&
      oci_containerengine_node_pool.demo.node_shape_config[0].memory_in_gbs == 12 &&
      oci_containerengine_node_pool.demo.node_metadata["areLegacyImdsEndpointsDisabled"] == "true" &&
      oci_containerengine_node_pool.demo.node_source_details[0].boot_volume_size_in_gbs == "50"
    )
    error_message = "The worker must remain one A1 at 2 OCPU/12GB with 50GB boot and IMDSv2-only metadata."
  }
  assert {
    condition = alltrue([
      for rule in oci_core_security_list.api.ingress_security_rules : rule.source != "0.0.0.0/0"
    ])
    error_message = "The API endpoint must not admit world ingress."
  }
  assert {
    condition = alltrue([
      for rule in oci_core_security_list.workers.ingress_security_rules : rule.protocol == "1" || rule.source != "0.0.0.0/0"
    ])
    error_message = "Workers must not admit public TCP/UDP/SSH ingress."
  }
  assert {
    condition = (
      length(oci_core_subnet.api.security_list_ids) == 1 &&
      length(oci_core_subnet.workers.security_list_ids) == 1 &&
      length(oci_core_subnet.ingress_reserved.security_list_ids) == 1
    )
    error_message = "Each subnet must use only its reviewed security list."
  }
  assert {
    condition = (
      oci_core_subnet.workers_private.prohibit_public_ip_on_vnic &&
      oci_core_public_ip.worker_nat.lifetime == "RESERVED" &&
      !oci_core_nat_gateway.workers.block_traffic &&
      oci_core_subnet.workers_private.cidr_block == "10.77.3.0/24" &&
      one(oci_core_route_table.workers.route_rules).destination == "0.0.0.0/0"
    )
    error_message = "Workers must launch in a private subnet with reserved-IP NAT egress."
  }
  assert {
    condition     = local.api_cidr == "10.77.0.0/28" && local.workers_cidr == "10.77.1.0/24" && local.ingress_cidr == "10.77.2.0/24"
    error_message = "The isolated subnet allocations must remain nonoverlapping."
  }
}

run "reject_world_api_access" {
  command = plan
  variables {
    admin_access_cidrs = ["0.0.0.0/0"]
  }
  expect_failures = [var.admin_access_cidrs]
}

# Enabled UI rules contain provider-computed nested set fields that remain
# unknown in mock plans. Validate those rules with the fresh read-only live
# plan; never use mock apply or remove prevent_destroy just to resolve them.
run "reject_ui_backend_outside_project" {
  command = plan
  variables {
    public_ui_backend_ipv4 = "203.0.113.48"
  }
  expect_failures = [var.public_ui_backend_ipv4]
}

run "reject_malformed_region" {
  command = plan
  variables {
    region = "not a region"
  }
  expect_failures = [var.region]
}

run "accept_chicago_image_region" {
  command = plan
  variables {
    region              = "us-chicago-1"
    availability_domain = "TEST:US-CHICAGO-1-AD-1"
    node_image_ocid     = "ocid1.image.oc1.us-chicago-1.mock-test-only"
  }
  assert {
    condition     = oci_containerengine_node_pool.demo.node_source_details[0].image_id == var.node_image_ocid
    error_message = "A valid hyphenated OCID region must be accepted and retained for the worker image."
  }
}

run "reuse_existing_project_compartment" {
  command = plan
  variables {
    existing_compartment_ocid = "ocid1.compartment.oc1..mockexistingproject"
  }
  assert {
    condition = (
      length(oci_identity_compartment.demo) == 0 &&
      oci_containerengine_cluster.demo.compartment_id == var.existing_compartment_ocid &&
      oci_core_vcn.demo.compartment_id == var.existing_compartment_ocid
    )
    error_message = "A second regional stack must use the verified compartment without creating or managing another compartment."
  }
}

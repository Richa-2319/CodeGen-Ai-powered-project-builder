locals {
  tags = {
    Project     = "codegen-demo"
    Environment = "demonstration"
    ManagedBy   = "Terraform"
  }
  api_cidr               = cidrsubnet(var.vcn_cidr, 12, 0)
  workers_cidr           = cidrsubnet(var.vcn_cidr, 8, 1)
  ingress_cidr           = cidrsubnet(var.vcn_cidr, 8, 2)
  private_workers_cidr   = cidrsubnet(var.vcn_cidr, 8, 3)
  project_compartment_id = var.existing_compartment_ocid == null ? oci_identity_compartment.demo[0].id : data.oci_identity_compartment.existing[0].id
}

resource "oci_identity_compartment" "demo" {
  count          = var.existing_compartment_ocid == null ? 1 : 0
  compartment_id = var.parent_compartment_ocid
  name           = "codegen-demo"
  description    = "Isolated CodeGen demonstration; reviewed capacity and billing required."
  enable_delete  = false
  freeform_tags  = local.tags

  lifecycle {
    prevent_destroy = true
  }
}

moved {
  from = oci_identity_compartment.demo
  to   = oci_identity_compartment.demo[0]
}

data "oci_identity_compartment" "existing" {
  count = var.existing_compartment_ocid == null ? 0 : 1
  id    = var.existing_compartment_ocid

  lifecycle {
    postcondition {
      condition     = self.compartment_id == var.parent_compartment_ocid && self.state == "ACTIVE"
      error_message = "The existing project compartment must be active and beneath the approved parent."
    }
  }
}

resource "oci_core_vcn" "demo" {
  compartment_id = local.project_compartment_id
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "codegen-demo-vcn"
  dns_label      = "codegendemo"
  freeform_tags  = local.tags
}

resource "oci_core_internet_gateway" "demo" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-igw"
  enabled        = true
  freeform_tags  = local.tags
}

resource "oci_core_route_table" "public" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-public-routes"
  freeform_tags  = local.tags
  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.demo.id
  }
}

# Reserved IPs avoid relying on a tenancy's ephemeral-public-IP allowance.
resource "oci_core_public_ip" "worker_nat" {
  compartment_id = local.project_compartment_id
  lifetime       = "RESERVED"
  display_name   = "codegen-demo-worker-egress"
  freeform_tags  = local.tags
}

resource "oci_core_nat_gateway" "workers" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-workers-nat"
  public_ip_id   = oci_core_public_ip.worker_nat.id
  block_traffic  = false
  freeform_tags  = local.tags
}

resource "oci_core_route_table" "workers" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-private-worker-routes"
  freeform_tags  = local.tags
  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_nat_gateway.workers.id
  }
}

# Each subnet uses its own explicit security list. The VCN default list,
# including any default SSH permission, is not associated with these subnets.
resource "oci_core_security_list" "api" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-api-security"
  freeform_tags  = local.tags

  dynamic "ingress_security_rules" {
    for_each = var.admin_access_cidrs
    content {
      protocol = "6"
      source   = ingress_security_rules.value
      tcp_options {
        min = 6443
        max = 6443
      }
    }
  }
  dynamic "ingress_security_rules" {
    for_each = toset([6443, 12250])
    content {
      protocol = "6"
      source   = local.private_workers_cidr
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }
  ingress_security_rules {
    protocol = "1"
    source   = local.private_workers_cidr
    icmp_options {
      type = 3
      code = 4
    }
  }
  egress_security_rules {
    protocol    = "6"
    destination = "0.0.0.0/0"
    tcp_options {
      min = 443
      max = 443
    }
  }
  egress_security_rules {
    protocol    = "6"
    destination = local.private_workers_cidr
  }
  egress_security_rules {
    protocol    = "1"
    destination = local.private_workers_cidr
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_security_list" "workers" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-workers-security"
  freeform_tags  = local.tags

  # Preserve the deployed NLB listener/health rules and managed HTTPS backend.
  # The private worker accepts these ports only from the project ingress subnet.
  dynamic "ingress_security_rules" {
    for_each = var.public_ui_backend_ipv4 == null ? {} : {
      "10256" = null
      "32080" = null
      "32443" = null
      "32081" = "CodeGen managed HTTPS gateway to private frontend backend"
    }
    content {
      protocol    = "6"
      source      = local.ingress_cidr
      description = ingress_security_rules.value
      tcp_options {
        min = tonumber(ingress_security_rules.key)
        max = tonumber(ingress_security_rules.key)
      }
    }
  }

  ingress_security_rules {
    protocol = "all"
    source   = local.private_workers_cidr
  }
  # Flannel requires control-plane TCP access to worker/pod ports, not only 10250.
  ingress_security_rules {
    protocol = "6"
    source   = local.api_cidr
  }
  ingress_security_rules {
    protocol = "1"
    source   = "0.0.0.0/0"
    icmp_options {
      type = 3
      code = 4
    }
  }
  egress_security_rules {
    protocol    = "all"
    destination = local.private_workers_cidr
  }
  # Private managed workers reach image registries, OKE, OCI, AI and Stripe
  # through NAT. No worker VNIC receives a public IP.
  egress_security_rules {
    protocol    = "6"
    destination = "0.0.0.0/0"
  }
  dynamic "egress_security_rules" {
    for_each = toset([53, 123])
    content {
      protocol    = "17"
      destination = "169.254.169.254/32"
      udp_options {
        min = egress_security_rules.value
        max = egress_security_rules.value
      }
    }
  }
  egress_security_rules {
    protocol    = "1"
    destination = "0.0.0.0/0"
    icmp_options {
      type = 3
      code = 4
    }
  }
}

resource "oci_core_security_list" "ingress_reserved" {
  compartment_id = local.project_compartment_id
  vcn_id         = oci_core_vcn.demo.id
  display_name   = "codegen-demo-future-ingress-closed"
  freeform_tags  = local.tags
  # NLB/API Gateway resources are managed separately. Keep their reviewed rules
  # here so a future infrastructure plan does not remove the working UI path.
  dynamic "ingress_security_rules" {
    for_each = var.public_ui_backend_ipv4 == null ? toset([]) : toset([80, 443])
    content {
      protocol = "6"
      source   = "0.0.0.0/0"
      tcp_options {
        min = ingress_security_rules.value
        max = ingress_security_rules.value
      }
    }
  }
  dynamic "egress_security_rules" {
    for_each = var.public_ui_backend_ipv4 == null ? toset([]) : toset([10256, 32080, 32443])
    content {
      protocol    = "6"
      destination = local.private_workers_cidr
      tcp_options {
        min = egress_security_rules.value
        max = egress_security_rules.value
      }
    }
  }
  dynamic "egress_security_rules" {
    for_each = var.public_ui_backend_ipv4 == null ? [] : [var.public_ui_backend_ipv4]
    content {
      protocol    = "6"
      destination = "${egress_security_rules.value}/32"
      description = "CodeGen managed HTTPS gateway to project worker frontend"
      tcp_options {
        min = 32081
        max = 32081
      }
    }
  }
}

resource "oci_core_subnet" "api" {
  compartment_id             = local.project_compartment_id
  vcn_id                     = oci_core_vcn.demo.id
  cidr_block                 = local.api_cidr
  display_name               = "codegen-demo-api"
  dns_label                  = "cgapi"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.api.id]
  dhcp_options_id            = oci_core_vcn.demo.default_dhcp_options_id
  freeform_tags              = local.tags
}

resource "oci_core_subnet" "workers" {
  # Retained unused from the original public-worker plan: OCI cannot change
  # subnet access type in place. Removing this subnet requires separate review.
  compartment_id             = local.project_compartment_id
  vcn_id                     = oci_core_vcn.demo.id
  cidr_block                 = local.workers_cidr
  display_name               = "codegen-demo-workers"
  dns_label                  = "cgworkers"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.workers.id]
  dhcp_options_id            = oci_core_vcn.demo.default_dhcp_options_id
  freeform_tags              = local.tags
}

resource "oci_core_subnet" "workers_private" {
  compartment_id             = local.project_compartment_id
  vcn_id                     = oci_core_vcn.demo.id
  cidr_block                 = local.private_workers_cidr
  display_name               = "codegen-demo-private-workers"
  dns_label                  = "cgprivate"
  prohibit_public_ip_on_vnic = true
  route_table_id             = oci_core_route_table.workers.id
  security_list_ids          = [oci_core_security_list.workers.id]
  dhcp_options_id            = oci_core_vcn.demo.default_dhcp_options_id
  freeform_tags              = local.tags
}

resource "oci_core_subnet" "ingress_reserved" {
  compartment_id             = local.project_compartment_id
  vcn_id                     = oci_core_vcn.demo.id
  cidr_block                 = local.ingress_cidr
  display_name               = "codegen-demo-future-ingress"
  dns_label                  = "cgingress"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.ingress_reserved.id]
  dhcp_options_id            = oci_core_vcn.demo.default_dhcp_options_id
  freeform_tags              = local.tags
}

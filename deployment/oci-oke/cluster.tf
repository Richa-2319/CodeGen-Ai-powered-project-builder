resource "oci_containerengine_cluster" "demo" {
  compartment_id     = local.project_compartment_id
  name               = "codegen-demo"
  kubernetes_version = var.kubernetes_version
  vcn_id             = oci_core_vcn.demo.id
  type               = "BASIC_CLUSTER"
  freeform_tags      = local.tags

  cluster_pod_network_options {
    cni_type = "FLANNEL_OVERLAY"
  }
  endpoint_config {
    is_public_ip_enabled = true
    subnet_id            = oci_core_subnet.api.id
  }
  options {
    service_lb_subnet_ids = [oci_core_subnet.ingress_reserved.id]
    kubernetes_network_config {
      pods_cidr     = "10.244.0.0/16"
      services_cidr = "10.96.0.0/16"
    }
    add_ons {
      is_kubernetes_dashboard_enabled = false
      is_tiller_enabled               = false
    }
  }

  lifecycle {
    prevent_destroy = true
  }
}

resource "oci_containerengine_node_pool" "demo" {
  compartment_id     = local.project_compartment_id
  cluster_id         = oci_containerengine_cluster.demo.id
  name               = "codegen-demo-a1"
  kubernetes_version = var.kubernetes_version
  node_shape         = "VM.Standard.A1.Flex"
  freeform_tags      = local.tags
  node_metadata = {
    areLegacyImdsEndpointsDisabled = "true"
  }

  node_shape_config {
    ocpus         = 2
    memory_in_gbs = 12
  }
  node_config_details {
    size          = 1
    freeform_tags = local.tags
    placement_configs {
      availability_domain = var.availability_domain
      subnet_id           = oci_core_subnet.workers_private.id
    }
  }
  node_source_details {
    source_type             = "IMAGE"
    image_id                = var.node_image_ocid
    boot_volume_size_in_gbs = "50"
  }
  initial_node_labels {
    key   = "codegen-role"
    value = "demo"
  }

  # No SSH key, autoscaler, instance metadata secrets or automatic node cycling.
  depends_on = [oci_core_security_list.api, oci_core_security_list.workers]
  lifecycle {
    prevent_destroy = true
  }
}

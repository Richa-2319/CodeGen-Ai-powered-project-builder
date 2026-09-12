output "compartment_ocid" {
  value = local.project_compartment_id
}

output "cluster_ocid" {
  value = oci_containerengine_cluster.demo.id
}

output "node_pool_ocid" {
  value = oci_containerengine_node_pool.demo.id
}

output "future_ingress_subnet_ocid" {
  value = oci_core_subnet.ingress_reserved.id
}

output "region" {
  value = var.region
}

output "reviewed_allocation" {
  value = {
    cluster_type                   = "BASIC_CLUSTER"
    managed_workers                = 1
    worker_ocpus                   = 2
    worker_memory_gb               = 12
    worker_boot_volume_gb          = 50
    later_application_pvc_count    = 3
    later_application_storage_gib  = 150
    total_boot_plus_application_gb = 200
    load_balancers_created         = 0
    nat_gateways_created           = 0
    estimated_price                = "NOT DETERMINED; verify selected tenancy eligibility, existing usage and current prices"
  }
}

terraform {
  required_version = ">= 1.12.0, < 2.0.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 8.27.0"
    }
  }

  # Initialize with an explicit state path outside the repository.
  backend "local" {}
}

provider "oci" {
  auth                = "SecurityToken"
  config_file_profile = var.oci_profile
  region              = var.region
  tenancy_ocid        = var.tenancy_ocid
}

variable "tenancy_ocid" {
  description = "Explicit target tenancy. Authentication stays in the selected external OCI profile."
  type        = string
  validation {
    condition     = can(regex("^ocid1\\.tenancy\\.oc[0-9]+\\.\\.[A-Za-z0-9]+$", var.tenancy_ocid))
    error_message = "Supply the explicitly selected OCI tenancy OCID."
  }
}

variable "region" {
  description = "The user-selected subscribed OCI deployment region; this need not be the tenancy home region."
  type        = string
  default     = "us-phoenix-1"
  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z0-9]+)+-[0-9]+$", var.region))
    error_message = "Supply an OCI region identifier confirmed for the selected tenancy."
  }
}

variable "parent_compartment_ocid" {
  description = "Existing parent under which the new codegen-demo compartment will be created."
  type        = string
  validation {
    condition     = can(regex("^ocid1\\.(tenancy|compartment)\\.oc[0-9]+\\.", var.parent_compartment_ocid))
    error_message = "Provide the explicitly approved parent tenancy or compartment OCID."
  }
}

variable "existing_compartment_ocid" {
  description = "Optional existing project compartment, managed by another regional stack. Null creates a new compartment."
  type        = string
  default     = null
  validation {
    condition     = var.existing_compartment_ocid == null || can(regex("^ocid1\\.compartment\\.oc[0-9]+\\.\\.[A-Za-z0-9]+$", var.existing_compartment_ocid))
    error_message = "Provide a verified existing compartment OCID or null."
  }
}

variable "availability_domain" {
  description = "Availability domain returned by current options for this tenancy and selected image."
  type        = string
  validation {
    condition     = can(regex("^[A-Za-z0-9]+:[A-Z0-9-]+-AD-[0-9]+$", var.availability_domain))
    error_message = "Provide the exact tenancy-qualified availability-domain name."
  }
}

variable "node_image_ocid" {
  description = "Current OKE ARM64 Oracle Linux image compatible with the selected Kubernetes version."
  type        = string
  validation {
    condition     = can(regex("^ocid1\\.image\\.oc[0-9]+\\.[a-z0-9]+(-[a-z0-9]+)*\\.", var.node_image_ocid))
    error_message = "Provide a confirmed target-region OKE ARM64 image OCID; generic OS images are not suitable."
  }
}

variable "kubernetes_version" {
  description = "Version confirmed in current OKE cluster/node-pool options."
  type        = string
  default     = "v1.35.2"
  validation {
    condition     = can(regex("^v1\\.[0-9]+\\.[0-9]+$", var.kubernetes_version))
    error_message = "Use an exact supported Kubernetes version, including the v prefix."
  }
}

variable "admin_access_cidrs" {
  description = "One to four confirmed public egress IPv4 CIDRs for operators reaching TCP6443; no default."
  type        = set(string)
  validation {
    condition = length(var.admin_access_cidrs) >= 1 && length(var.admin_access_cidrs) <= 4 && alltrue([
      for cidr in var.admin_access_cidrs : can(cidrnetmask(cidr)) && try(tonumber(split("/", cidr)[1]) >= 24, false)
    ])
    error_message = "Supply 1-4 narrow IPv4 operator egress CIDRs (/24 through /32). World-accessible API rules are prohibited."
  }
}

variable "vcn_cidr" {
  description = "Isolated IPv4 /16; confirm it does not overlap any future peered/corporate network."
  type        = string
  default     = "10.77.0.0/16"
  validation {
    condition = can(cidrnetmask(var.vcn_cidr)) && try(
      tonumber(split("/", var.vcn_cidr)[1]) == 16 &&
      !contains(["10.244.0.0", "10.96.0.0"], cidrhost(var.vcn_cidr, 0)), false
    )
    error_message = "Use a /16 IPv4 CIDR that does not overlap the flannel pod or Kubernetes service CIDRs."
  }
}

variable "oci_profile" {
  description = "Required external SecurityToken profile authorized for the explicitly selected hosting tenancy."
  type        = string
  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+$", var.oci_profile))
    error_message = "Use the name of an already configured external OCI profile."
  }
}

variable "public_ui_backend_ipv4" {
  description = "Verified private worker IPv4 for the deployed managed HTTPS UI. Null keeps the ingress subnet closed."
  type        = string
  default     = null
  validation {
    condition = var.public_ui_backend_ipv4 == null || try(
      cidrhost("${var.public_ui_backend_ipv4}/24", 0) == cidrhost(cidrsubnet(var.vcn_cidr, 8, 3), 0), false
    )
    error_message = "The UI backend must be an IPv4 address inside this project's private worker subnet."
  }
}

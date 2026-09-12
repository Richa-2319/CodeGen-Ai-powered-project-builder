ARG CA_IMAGE=docker.io/library/eclipse-temurin@sha256:9b33cd477f0d297008960ca6b81bdcc86915e7e82676653b615173899319d995
FROM ${CA_IMAGE} AS certificates
FROM scratch
COPY --from=certificates /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt
COPY codegen-ocir-auth /codegen-ocir-auth
USER 65532:65532
ENTRYPOINT ["/codegen-ocir-auth"]

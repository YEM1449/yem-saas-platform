# Module 17: Object Storage

## Why This Matters

Media and documents outgrow local disk quickly in real deployments.

## Learning Goals

- understand the storage abstraction
- understand local versus S3-compatible operation
- understand provider-specific pitfalls

## Storage Strategy

- local disk for development and simple environments
- object storage for production-style deployments

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/media/service/LocalFileMediaStorage.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/media/service/LocalFileMediaStorage.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/media/service/ObjectStorageMediaStorage.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/media/service/ObjectStorageMediaStorage.java)
- [../guides/engineer/object-storage.md](../guides/engineer/object-storage.md)

## Key Insight

The storage abstraction protects business code from vendor details, but the adapter still has to understand real provider behavior such as path-style URLs.

## Exercise

Explain why path-style addressing is an architectural decision in this codebase and not just an incidental implementation detail.

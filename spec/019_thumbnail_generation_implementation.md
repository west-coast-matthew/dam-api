status: completed

# 019 — thumbnail generation implementation

## Summary

We have established a thumbnail generation service in another spec. We now need to support the ability to call that service to produce and persist thumbnails when the creation api endpoint is called.

Image extraction and conversion is a relatively expensive operation. bulk asset insertion is a common use case, resulting in potentially lengthy delays. Fortunately it is understood that data and assets are persisted immediately and the thumbnails will assume an 'eventually consistent' state.

Given the fact that bulk uploads may be large in number, it is desirable once assets are persisted to immediately return a response to the client and handle the request in an asynchronous manner. Furthermore, it is desirable to handle several generation requests in a parallel manner (i.e. concurrent threads).

202 accepted status should be returned.

# Tasks
* Update existing asset and bulk asset creation workflows to generate thumbnails based on the appropriate media type. Return 202 code once entities are persisted and handle thumbnail requests in an asynch & parallel fashion.


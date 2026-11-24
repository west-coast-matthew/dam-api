# thumbnail generation

status: completed

## Goal

We need to support the ability to generate thumbnails for uploaded assets. For images, this means scaled down versions of the original image, for video we will take a screenshot of a given frame at a defined interval and use that for the video thumbnail, for PDFs, we want to use the first page as the thumbnail, so that would need to be exported as an image, and we will simply use a static image for audio type assets.

## ThumbnailGenerationService
This new service will abstract the rest of the codebase from the implementation details such as how the images are generated. The methods should accept input as binary data or streams as this is what an API call will accept, and should return binary data. This service will be abstracted from the details of how the thumbnails are persisted, that will be another service's responsibility.

This approach will involve 'pluggable' implementations. For example the caller passes in an image asset, however we may want to implement a local command line gimp command, for other deployments it might be appropriate to delegate the task to another api.

The aspect ratio will remain intact preserving landscape or horizontal as the client is responsible for displaying the data.

We need to support the following image formats:
jpg
jpeg
gif
png
svg
bmp
psd

We need the ability to extract an image of the first page for PDF media.

We need the ability to extract an image at a given point in time for thumbnail extraction in the following formats:

- avi
- mkv
- mp4
- mov

We need to review which libraries to use to meet the above criteria.

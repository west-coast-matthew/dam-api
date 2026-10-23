# thumbnail generation

**Status:** ready

## Goal

We need to support the ability to generate thumbnails of assets.

TimeConstants has been introduced. Instead of simply using a default naming convenstion such as ImageConversionServiceImpl, create implementation specific versions. So for example, ImageMagicImageConversionService. We want the ability to plug in different implementations. This should be based on application property definitions, but at this point in time, we will only have a single implementation. Spring based wiring should be used to wire the processing implementation. There will be only a single implementation provided at runtime, although multiple might be defined in the futre. essentially a factory based approach for the implementation via available spring framework tooling.

## Requirements

* create a service implementation for the placeholder ImageService interface. currently we expose two public facing methods, however they should call the same internal method passing in the appropriate max width and height values based on if we are producing a thumbnail, or larger preview version. 
* Isolate plublic accessible methods from internal methods which perform the heavy listing
* create a service implementation for the VideoService defined methods.
* ensure method definitions in video service use the default constants provided, modify the method signatures to accept these values optionally. current definitions dont reflect this.
* update the videoservice interface to use constants defined in time constants and size constants.
* update the image service to also use timeconstants 
* implement the new missing ImageProcessingException and VideoProcessingException
* add checks into the heart beat that validate required processing libraries are present on the file system.
* Add unit tests, however limit the scope to parameter validation.
* ensure support for image formats jpeg, webp, standat formats
* following this approach, create a new PDF service that will return a thumbnail for pdf data, defaulting to the first page, with support for defining a desired page.


## Guidance for establishing image thunbnail sizes

Tier 1: Browse / Grid View (The "Pinterest" Size)
Target UI Render Size: ~250px wide (fluid masonry grid).

Recommended Storage Dimensions (2x Retina Buffer):

Max Width: 500px

Max Height: 750px (Assuming a standard 2:3 vertical aspect ratio typical of reference art and Pinterest)

Why: If a user’s browser displays an image card at roughly 250px wide, serving a 500px wide image ensures it looks razor-sharp on MacBook Retina screens and high-end mobile devices without wasting bandwidth downloading a multi-megabyte original.

Tier 2: Mouse-Over / Hover Preview (~50% Larger)
Target UI Render Size: ~375px to 450px wide (expanded tooltip, card hover zoom, or quick-peek modal).

Recommended Storage Dimensions (50% larger than Tier 1):

Max Width: 750px

Max Height: 1125px

Why: When a user hovers over an asset to inspect detail (like brush strokes in reference art or fine text in a PDF/design layout), a 750px bounding box provides enough pixel density to discern details without triggering a full-resolution fetch.


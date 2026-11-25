status: completed

# 020 — track recent assets uploads

## Summary

We need to establish the ability for the front end to get access to data related to asset upload activity. For example, recent asset uploads, etc. We have this data, so I don't think we need to establish any new entities to persist it. Instead, I want to use an optimized SQL approach to locate the data. 

We will most definitely need to create a DTO to wrapper the payload.

Filter by last x time period, default to last 24 hours, but support common time increments such as last 15 minutes, 5 minutes, hour, 8 hours, etc. 

Filter based on a selected category. So given a category, we display statistics for that category. if there 

Example request/response:

The following is a request response for a selected category with no child categories

```
/api/assets/activity?categoryid=#$@fsrfREf@#$2
```

```
{
	lastSampleTimePeriod: 289382323,
	totalAssetUploadOperations: 100,
	totalAssetsUploaded: 342873643234,
	categoryName: 'some category name',
	uploadAttempts: [
		{ dateTime: 32443289732947, uploadCount:, fileType: }
	],
	recentAssets:[
		{
			id: 32fr324234,
			title: 'asset #1'
		}
	]
	
}

```
However the signature is a bit different when the selected category has child categories, in which case we include summary information for each sub category as follows:

```
{
	lastSampleTimePeriod: 289382323,
	totalAssetUploadOperations: 100,
	totalAssetsUploaded: 342873643234,
	categoyName: 'some category name',
	...
	recentAssets:[
		{
			id: 32fr324234,
			title: 'asset #1',
			subCategories: [
				{
					title: 'subcategory 1',
					newAssetCount: 100
				}
			]
		}
	]
	
}

```

The uploadIncidents attribute may be derived by searching for all new assets within the time window as specified (i.e. last 24 hours) and then summarizing the count and date time.

The file type for bulk operations will reflect 'image', 'video', 'pdf', etc.. unless mixed content was supplied, in which case we simply use the value 'mixed'.

package com.example.inventory;

import org.springframework.web.bind.annotation.*;

/** Supplier restock feeds: the feed's text is the request body. */
@RestController
@RequestMapping("/api/supplier-feed")
@CrossOrigin(origins = "*")
public class SupplierFeedController {

    private final SupplierFeedImporter importer;

    public SupplierFeedController(SupplierFeedImporter importer) {
        this.importer = importer;
    }

    @PostMapping(consumes = "text/plain")
    public SupplierFeedImporter.FeedResult upload(@RequestParam String supplier, @RequestBody String feed) {
        return importer.importFeed(supplier, feed);
    }
}

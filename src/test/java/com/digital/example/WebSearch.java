package com.digital.example;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class WebSearch {

    public static void main(String[] args) {
        HttpResponse<String> response = Unirest.post("https://api.tavily.com/search")
                .header("Authorization", "Bearer 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")
                .header("Content-Type", "application/json")
                .body("{\n  \"query\": \"谁是彭于晏?\",\n  \"auto_parameters\": false,\n  \"topic\": \"general\",\n  \"search_depth\": \"basic\",\n  \"chunks_per_source\": 3,\n  \"max_results\": 1,\n  \"time_range\": null,\n  \"start_date\": \"2025-02-09\",\n  \"end_date\": \"2025-12-29\",\n  \"include_answer\": true,\n  \"include_raw_content\": true,\n  \"include_images\": false,\n  \"include_image_descriptions\": false,\n  \"include_favicon\": false,\n  \"include_domains\": [],\n  \"exclude_domains\": [],\n  \"country\": null\n}")
                .asString();

        System.out.println(response.getBody());
    }
}

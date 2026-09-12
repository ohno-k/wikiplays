package com.wikiplays.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 記事キャッシュ (cached_article.data_json) の読み書き用 ObjectMapper。
 * ArticleData にフィールドを追加・削除しても古い JSON を読めるよう、未知プロパティで失敗しない。
 * (Spring の HTTP 用 ObjectMapper Bean とは別物。Bean にすると自動構成の方を上書きしてしまう。)
 */
public final class ArticleJson {

    private ArticleJson() {}

    public static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}

package com.wikiplays.dto;

public record PlayQuotaResponse(
    /** プレミアム加入で無制限。 */
    boolean unlimited,
    /** 今日プレイした回数 (mode=a)。 */
    long played,
    /** 今日プレイ可能な総数 (Free=5、Premium=Integer.MAX_VALUE)。 */
    int limit,
    /** 残りプレイ可能数。 */
    int remaining
) {}

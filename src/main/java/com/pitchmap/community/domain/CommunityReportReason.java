package com.pitchmap.community.domain;

/** 글이나 댓글을 신고하는 사유. */
public enum CommunityReportReason {
    /** 스팸이나 광고다. */
    SPAM,
    /** 욕설이나 혐오 표현이다. */
    ABUSE,
    /** 불법 야영을 부추긴다. */
    ILLEGAL_CAMPING,
    /** 개인정보를 드러낸다. */
    PRIVACY,
    /** 금전을 요구하거나 사기가 의심된다. */
    MONEY_SCAM,
    /** 위에 없는 사유다. */
    OTHER
}

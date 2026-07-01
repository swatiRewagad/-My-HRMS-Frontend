package com.hrms.cms.entity;

public enum SupportedLocale {
    EN("en", "English", "English", false),
    HI("hi", "Hindi", "हिन्दी", false),
    BN("bn", "Bengali", "বাংলা", false),
    MR("mr", "Marathi", "मराठी", false),
    TE("te", "Telugu", "తెలుగు", false),
    TA("ta", "Tamil", "தமிழ்", false),
    GU("gu", "Gujarati", "ગુજરાતી", false),
    UR("ur", "Urdu", "اردو", true),
    KN("kn", "Kannada", "ಕನ್ನಡ", false),
    ML("ml", "Malayalam", "മലയാളം", false);

    private final String code;
    private final String nameEn;
    private final String nativeName;
    private final boolean rtl;

    SupportedLocale(String code, String nameEn, String nativeName, boolean rtl) {
        this.code = code;
        this.nameEn = nameEn;
        this.nativeName = nativeName;
        this.rtl = rtl;
    }

    public String getCode() { return code; }
    public String getNameEn() { return nameEn; }
    public String getNativeName() { return nativeName; }
    public boolean isRtl() { return rtl; }

    public static SupportedLocale fromCode(String code) {
        for (SupportedLocale locale : values()) {
            if (locale.code.equalsIgnoreCase(code)) return locale;
        }
        return EN;
    }

    public static boolean isSupported(String code) {
        for (SupportedLocale locale : values()) {
            if (locale.code.equalsIgnoreCase(code)) return true;
        }
        return false;
    }
}

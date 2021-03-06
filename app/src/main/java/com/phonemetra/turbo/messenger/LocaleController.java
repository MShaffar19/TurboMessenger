/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package com.phonemetra.turbo.messenger;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Xml;

import com.phonemetra.turbo.messenger.time.FastDateFormat;
import com.phonemetra.turbo.tgnet.ConnectionsManager;
import com.phonemetra.turbo.tgnet.RequestDelegate;
import com.phonemetra.turbo.tgnet.TLObject;
import com.phonemetra.turbo.tgnet.TLRPC;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class LocaleController {

    static final int QUANTITY_OTHER = 0x0000;
    static final int QUANTITY_ZERO = 0x0001;
    static final int QUANTITY_ONE = 0x0002;
    static final int QUANTITY_TWO = 0x0004;
    static final int QUANTITY_FEW = 0x0008;
    static final int QUANTITY_MANY = 0x0010;

    public static boolean isRTL = false;
    public static int nameDisplayOrder = 1;
    private static boolean is24HourFormat = false;
    public FastDateFormat formatterDay;
    public FastDateFormat formatterWeek;
    public FastDateFormat formatterMonth;
    public FastDateFormat formatterYear;
    public FastDateFormat formatterMonthYear;
    public FastDateFormat formatterYearMax;
    public FastDateFormat formatterStats;
    public FastDateFormat formatterBannedUntil;
    public FastDateFormat formatterBannedUntilThisYear;
    public FastDateFormat chatDate;
    public FastDateFormat chatFullDate;

    private HashMap<String, PluralRules> allRules = new HashMap<>();

    private Locale currentLocale;
    private Locale systemDefaultLocale;
    private PluralRules currentPluralRules;
    private LocaleInfo currentLocaleInfo;
    private HashMap<String, String> localeValues = new HashMap<>();
    private String languageOverride;
    private boolean changingConfiguration = false;
    private boolean reloadLastFile;

    private HashMap<String, String> currencyValues;
    private HashMap<String, String> translitChars;

    private class TimeZoneChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ApplicationLoader.applicationHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!formatterMonth.getTimeZone().equals(TimeZone.getDefault())) {
                        LocaleController.getInstance().recreateFormatters();
                    }
                }
            });
        }
    }

    public static class LocaleInfo {

        public String name;
        public String nameEnglish;
        public String shortName;
        public String pathToFile;
        public int version;
        public boolean builtIn;

        public String getSaveString() {
            return name + "|" + nameEnglish + "|" + shortName + "|" + pathToFile + "|" + version;
        }

        public static LocaleInfo createWithString(String string) {
            if (string == null || string.length() == 0) {
                return null;
            }
            String[] args = string.split("\\|");
            LocaleInfo localeInfo = null;
            if (args.length >= 4) {
                localeInfo = new LocaleInfo();
                localeInfo.name = args[0];
                localeInfo.nameEnglish = args[1];
                localeInfo.shortName = args[2].toLowerCase();
                localeInfo.pathToFile = args[3];
                if (args.length >= 5) {
                    localeInfo.version = Utilities.parseInt(args[4]);
                }
            }
            return localeInfo;
        }

        public File getPathToFile() {
            if (isRemote()) {
                return new File(ApplicationLoader.getFilesDirFixed(), "remote_" + shortName + ".xml");
            }
            return !TextUtils.isEmpty(pathToFile) ? new File(pathToFile) : null;
        }

        public String getKey() {
            if (pathToFile != null && !"remote".equals(pathToFile)) {
                return "local_" + shortName;
            }
            return shortName;
        }

        public boolean isRemote() {
            return "remote".equals(pathToFile);
        }

        public boolean isLocal() {
            return !TextUtils.isEmpty(pathToFile) && !isRemote();
        }

        public boolean isBuiltIn() {
            return builtIn;
        }
    }

    private boolean loadingRemoteLanguages;

    public ArrayList<LocaleInfo> languages = new ArrayList<>();
    public ArrayList<LocaleInfo> remoteLanguages = new ArrayList<>();
    public HashMap<String, LocaleInfo> languagesDict = new HashMap<>();

    private ArrayList<LocaleInfo> otherLanguages = new ArrayList<>();

    private static volatile LocaleController Instance = null;
    public static LocaleController getInstance() {
        LocaleController localInstance = Instance;
        if (localInstance == null) {
            synchronized (LocaleController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new LocaleController();
                }
            }
        }
        return localInstance;
    }

    public LocaleController() {
        addRules(new String[]{"bem", "brx", "da", "de", "el", "en", "eo", "es", "et", "fi", "fo", "gl", "he", "iw", "it", "nb",
                "nl", "nn", "no", "sv", "af", "bg", "bn", "ca", "eu", "fur", "fy", "gu", "ha", "is", "ku",
                "lb", "ml", "mr", "nah", "ne", "om", "or", "pa", "pap", "ps", "so", "sq", "sw", "ta", "te",
                "tk", "ur", "zu", "mn", "gsw", "chr", "rm", "pt", "an", "ast"}, new PluralRules_One());
        addRules(new String[]{"cs", "sk"}, new PluralRules_Czech());
        addRules(new String[]{"ff", "fr", "kab"}, new PluralRules_French());
        addRules(new String[]{"hr", "ru", "sr", "uk", "be", "bs", "sh"}, new PluralRules_Balkan());
        addRules(new String[]{"lv"}, new PluralRules_Latvian());
        addRules(new String[]{"lt"}, new PluralRules_Lithuanian());
        addRules(new String[]{"pl"}, new PluralRules_Polish());
        addRules(new String[]{"ro", "mo"}, new PluralRules_Romanian());
        addRules(new String[]{"sl"}, new PluralRules_Slovenian());
        addRules(new String[]{"ar"}, new PluralRules_Arabic());
        addRules(new String[]{"mk"}, new PluralRules_Macedonian());
        addRules(new String[]{"cy"}, new PluralRules_Welsh());
        addRules(new String[]{"br"}, new PluralRules_Breton());
        addRules(new String[]{"lag"}, new PluralRules_Langi());
        addRules(new String[]{"shi"}, new PluralRules_Tachelhit());
        addRules(new String[]{"mt"}, new PluralRules_Maltese());
        addRules(new String[]{"ga", "se", "sma", "smi", "smj", "smn", "sms"}, new PluralRules_Two());
        addRules(new String[]{"ak", "am", "bh", "fil", "tl", "guw", "hi", "ln", "mg", "nso", "ti", "wa"}, new PluralRules_Zero());
        addRules(new String[]{"az", "bm", "fa", "ig", "hu", "ja", "kde", "kea", "ko", "my", "ses", "sg", "to",
                "tr", "vi", "wo", "yo", "zh", "bo", "dz", "id", "jv", "jw", "ka", "km", "kn", "ms", "th", "in"}, new PluralRules_None());

        LocaleInfo localeInfo = new LocaleInfo();
        localeInfo.name = "English";
        localeInfo.nameEnglish = "English";
        localeInfo.shortName = "en";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Italiano";
        localeInfo.nameEnglish = "Italian";
        localeInfo.shortName = "it";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Espa??ol";
        localeInfo.nameEnglish = "Spanish";
        localeInfo.shortName = "es";
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Deutsch";
        localeInfo.nameEnglish = "German";
        localeInfo.shortName = "de";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Nederlands";
        localeInfo.nameEnglish = "Dutch";
        localeInfo.shortName = "nl";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "??????????????";
        localeInfo.nameEnglish = "Arabic";
        localeInfo.shortName = "ar";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "Portugu??s (Brasil)";
        localeInfo.nameEnglish = "Portuguese (Brazil)";
        localeInfo.shortName = "pt_br";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        localeInfo = new LocaleInfo();
        localeInfo.name = "?????????";
        localeInfo.nameEnglish = "Korean";
        localeInfo.shortName = "ko";
        localeInfo.pathToFile = null;
        localeInfo.builtIn = true;
        languages.add(localeInfo);
        languagesDict.put(localeInfo.shortName, localeInfo);

        loadOtherLanguages();
        if (remoteLanguages.isEmpty()) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    loadRemoteLanguages();
                }
            });
        }

        for (int a = 0; a < otherLanguages.size(); a++) {
            LocaleInfo locale = otherLanguages.get(a);
            languages.add(locale);
            languagesDict.put(locale.getKey(), locale);
        }

        for (int a = 0; a < remoteLanguages.size(); a++) {
            LocaleInfo locale = remoteLanguages.get(a);
            LocaleInfo existingLocale = getLanguageFromDict(locale.getKey());
            if (existingLocale != null) {
                existingLocale.pathToFile = locale.pathToFile;
                existingLocale.version = locale.version;
            } else {
                languages.add(locale);
                languagesDict.put(locale.getKey(), locale);
            }
        }

        systemDefaultLocale = Locale.getDefault();
        is24HourFormat = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        LocaleInfo currentInfo = null;
        boolean override = false;

        try {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            String lang = preferences.getString("language", null);
            if (lang != null) {
                currentInfo = getLanguageFromDict(lang);
                if (currentInfo != null) {
                    override = true;
                }
            }

            if (currentInfo == null && systemDefaultLocale.getLanguage() != null) {
                currentInfo = getLanguageFromDict(systemDefaultLocale.getLanguage());
            }
            if (currentInfo == null) {
                currentInfo = getLanguageFromDict(getLocaleString(systemDefaultLocale));
                if (currentInfo == null) {
                    currentInfo = getLanguageFromDict("en");
                }
            }

            applyLanguage(currentInfo, override, true);
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            IntentFilter timezoneFilter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ApplicationLoader.applicationContext.registerReceiver(new TimeZoneChangedReceiver(), timezoneFilter);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private LocaleInfo getLanguageFromDict(String key) {
        if (key == null) {
            return null;
        }
        return languagesDict.get(key.toLowerCase().replace("-", "_"));
    }

    private void addRules(String[] languages, PluralRules rules) {
        for (String language : languages) {
            allRules.put(language, rules);
        }
    }

    private String stringForQuantity(int quantity) {
        switch (quantity) {
            case QUANTITY_ZERO:
                return "zero";
            case QUANTITY_ONE:
                return "one";
            case QUANTITY_TWO:
                return "two";
            case QUANTITY_FEW:
                return "few";
            case QUANTITY_MANY:
                return "many";
            default:
                return "other";
        }
    }

    public Locale getSystemDefaultLocale() {
        return systemDefaultLocale;
    }

    public boolean isCurrentLocalLocale() {
        return currentLocaleInfo.isLocal();
    }

    public void reloadCurrentRemoteLocale() {
        applyRemoteLanguage(currentLocaleInfo, null, true);
    }

    private String getLocaleString(Locale locale) {
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('_');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getSystemLocaleStringIso639() {
        Locale locale = getInstance().getSystemDefaultLocale();
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('-');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getLocaleStringIso639() {
        Locale locale = getInstance().currentLocale;
        if (locale == null) {
            return "en";
        }
        String languageCode = locale.getLanguage();
        String countryCode = locale.getCountry();
        String variantCode = locale.getVariant();
        if (languageCode.length() == 0 && countryCode.length() == 0) {
            return "en";
        }
        StringBuilder result = new StringBuilder(11);
        result.append(languageCode);
        if (countryCode.length() > 0 || variantCode.length() > 0) {
            result.append('-');
        }
        result.append(countryCode);
        if (variantCode.length() > 0) {
            result.append('_');
        }
        result.append(variantCode);
        return result.toString();
    }

    public static String getLocaleAlias(String code) {
        if (code == null) {
            return null;
        }
        switch (code) {
            case "in":
                return "id";
            case "iw":
                return "he";
            case "jw":
                return "jv";
            case "no":
                return "nb";
            case "tl":
                return "fil";
            case "ji":
                return "yi";
            case "id":
                return "in";
            case "he":
                return "iw";
            case "jv":
                return "jw";
            case "nb":
                return "no";
            case "fil":
                return "tl";
            case "yi":
                return "ji";
        }

        return null;
    }

    public boolean applyLanguageFile(File file) {
        try {
            HashMap<String, String> stringMap = getLocaleFileStrings(file);

            String languageName = stringMap.get("LanguageName");
            String languageNameInEnglish = stringMap.get("LanguageNameInEnglish");
            String languageCode = stringMap.get("LanguageCode");

            if (languageName != null && languageName.length() > 0 &&
                    languageNameInEnglish != null && languageNameInEnglish.length() > 0 &&
                    languageCode != null && languageCode.length() > 0) {

                if (languageName.contains("&") || languageName.contains("|")) {
                    return false;
                }
                if (languageNameInEnglish.contains("&") || languageNameInEnglish.contains("|")) {
                    return false;
                }
                if (languageCode.contains("&") || languageCode.contains("|") || languageCode.contains("/") || languageCode.contains("\\")) {
                    return false;
                }

                File finalFile = new File(ApplicationLoader.getFilesDirFixed(), languageCode + ".xml");
                if (!AndroidUtilities.copyFile(file, finalFile)) {
                    return false;
                }

                LocaleInfo localeInfo = getLanguageFromDict(languageCode);
                if (localeInfo == null) {
                    localeInfo = new LocaleInfo();
                    localeInfo.name = languageName;
                    localeInfo.nameEnglish = languageNameInEnglish;
                    localeInfo.shortName = languageCode.toLowerCase();

                    localeInfo.pathToFile = finalFile.getAbsolutePath();
                    languages.add(localeInfo);
                    languagesDict.put(localeInfo.getKey(), localeInfo);
                    otherLanguages.add(localeInfo);

                    saveOtherLanguages();
                }
                localeValues = stringMap;
                applyLanguage(localeInfo, true, false, true, false);
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private void saveOtherLanguages() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("langconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        StringBuilder stringBuilder = new StringBuilder();
        for (int a = 0; a < otherLanguages.size(); a++) {
            LocaleInfo localeInfo = otherLanguages.get(a);
            String loc = localeInfo.getSaveString();
            if (loc != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append("&");
                }
                stringBuilder.append(loc);
            }
        }
        editor.putString("locales", stringBuilder.toString());
        stringBuilder.setLength(0);
        for (int a = 0; a < remoteLanguages.size(); a++) {
            LocaleInfo localeInfo = remoteLanguages.get(a);
            String loc = localeInfo.getSaveString();
            if (loc != null) {
                if (stringBuilder.length() != 0) {
                    stringBuilder.append("&");
                }
                stringBuilder.append(loc);
            }
        }
        editor.putString("remote", stringBuilder.toString());
        editor.commit();
    }

    public boolean deleteLanguage(LocaleInfo localeInfo) {
        if (localeInfo.pathToFile == null || localeInfo.isRemote()) {
            return false;
        }
        if (currentLocaleInfo == localeInfo) {
            LocaleInfo info = null;
            if (systemDefaultLocale.getLanguage() != null) {
                info = getLanguageFromDict(systemDefaultLocale.getLanguage());
            }
            if (info == null) {
                info = getLanguageFromDict(getLocaleString(systemDefaultLocale));
            }
            if (info == null) {
                info = getLanguageFromDict("en");
            }
            applyLanguage(info, true, false);
        }

        otherLanguages.remove(localeInfo);
        languages.remove(localeInfo);
        languagesDict.remove(localeInfo.shortName);
        File file = new File(localeInfo.pathToFile);
        file.delete();
        saveOtherLanguages();
        return true;
    }

    private void loadOtherLanguages() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("langconfig", Activity.MODE_PRIVATE);
        String locales = preferences.getString("locales", null);
        if (!TextUtils.isEmpty(locales)) {
            String[] localesArr = locales.split("&");
            for (String locale : localesArr) {
                LocaleInfo localeInfo = LocaleInfo.createWithString(locale);
                if (localeInfo != null) {
                    otherLanguages.add(localeInfo);
                }
            }
        }
        locales = preferences.getString("remote", null);
        if (!TextUtils.isEmpty(locales)) {
            String[] localesArr = locales.split("&");
            for (String locale : localesArr) {
                LocaleInfo localeInfo = LocaleInfo.createWithString(locale);
                localeInfo.shortName = localeInfo.shortName.replace("-", "_");
                if (localeInfo != null) {
                    remoteLanguages.add(localeInfo);
                }
            }
        }
    }

    private HashMap<String, String> getLocaleFileStrings(File file) {
        return getLocaleFileStrings(file, false);
    }

    private HashMap<String, String> getLocaleFileStrings(File file, boolean preserveEscapes) {
        FileInputStream stream = null;
        reloadLastFile = false;
        try {
            if (!file.exists()) {
                return new HashMap<>();
            }
            HashMap<String, String> stringMap = new HashMap<>();
            XmlPullParser parser = Xml.newPullParser();
            stream = new FileInputStream(file);
            parser.setInput(stream, "UTF-8");
            int eventType = parser.getEventType();
            String name = null;
            String value = null;
            String attrName = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    name = parser.getName();
                    int c = parser.getAttributeCount();
                    if (c > 0) {
                        attrName = parser.getAttributeValue(0);
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (attrName != null) {
                        value = parser.getText();
                        if (value != null) {
                            value = value.trim();
                            if (preserveEscapes) {
                                value = value.replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace("& ", "&amp; ");
                            } else {
                                value = value.replace("\\n", "\n");
                                value = value.replace("\\", "");
                                String old = value;
                                value = value.replace("&lt;", "<");
                                if (!reloadLastFile && !value.equals(old)) {
                                    reloadLastFile = true;
                                }
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    value = null;
                    attrName = null;
                    name = null;
                }
                if (name != null && name.equals("string") && value != null && attrName != null && value.length() != 0 && attrName.length() != 0) {
                    stringMap.put(attrName, value);
                    name = null;
                    value = null;
                    attrName = null;
                }
                eventType = parser.next();
            }
            return stringMap;
        } catch (Exception e) {
            FileLog.e(e);
            reloadLastFile = true;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return new HashMap<>();
    }

    public void applyLanguage(LocaleInfo localeInfo, boolean override, boolean init) {
        applyLanguage(localeInfo, override, init, false, false);
    }

    public void applyLanguage(final LocaleInfo localeInfo, boolean override, boolean init, boolean fromFile, boolean force) {
        if (localeInfo == null) {
            return;
        }
        File pathToFile = localeInfo.getPathToFile();
        String shortName = localeInfo.shortName;
        if (!init) {
            ConnectionsManager.getInstance().setLangCode(shortName.replace("_", "-"));
        }
        if (localeInfo.isRemote() && (force || !pathToFile.exists())) {
            FileLog.d("reload locale because file doesn't exist " + pathToFile);
            if (init) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        applyRemoteLanguage(localeInfo, null, true);
                    }
                });
            } else {
                applyRemoteLanguage(localeInfo, null, true);
            }
        }
        try {
            Locale newLocale;
            String[] args = localeInfo.shortName.split("_");
            if (args.length == 1) {
                newLocale = new Locale(localeInfo.shortName);
            } else {
                newLocale = new Locale(args[0], args[1]);
            }
            if (override) {
                languageOverride = localeInfo.shortName;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("language", localeInfo.getKey());
                editor.commit();
            }
            if (pathToFile == null) {
                localeValues.clear();
            } else if (!fromFile) {
                localeValues = getLocaleFileStrings(pathToFile);
            }
            currentLocale = newLocale;
            currentLocaleInfo = localeInfo;
            currentPluralRules = allRules.get(args[0]);
            if (currentPluralRules == null) {
                currentPluralRules = allRules.get(currentLocale.getLanguage());
            }
            if (currentPluralRules == null) {
                currentPluralRules = new PluralRules_None();
            }
            changingConfiguration = true;
            Locale.setDefault(currentLocale);
            android.content.res.Configuration config = new android.content.res.Configuration();
            config.locale = currentLocale;
            ApplicationLoader.applicationContext.getResources().updateConfiguration(config, ApplicationLoader.applicationContext.getResources().getDisplayMetrics());
            changingConfiguration = false;
            if (reloadLastFile) {
                if (init) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            reloadCurrentRemoteLocale();
                        }
                    });
                } else {
                    reloadCurrentRemoteLocale();
                }
                reloadLastFile = false;
            }
        } catch (Exception e) {
            FileLog.e(e);
            changingConfiguration = false;
        }
        recreateFormatters();
    }

    public LocaleInfo getCurrentLocaleInfo() {
        return currentLocaleInfo;
    }

    public static String getCurrentLanguageName() {
        return getString("LanguageName", R.string.LanguageName);
    }

    private String getStringInternal(String key, int res) {
        String value = localeValues.get(key);
        if (value == null) {
            try {
                value = ApplicationLoader.applicationContext.getString(res);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (value == null) {
            value = "LOC_ERR:" + key;
        }
        return value;
    }

    public static String getString(String key, int res) {
        return getInstance().getStringInternal(key, res);
    }

    public static String getPluralString(String key, int plural) {
        if (key == null || key.length() == 0 || getInstance().currentPluralRules == null) {
            return "LOC_ERR:" + key;
        }
        String param = getInstance().stringForQuantity(getInstance().currentPluralRules.quantityForNumber(plural));
        param = key + "_" + param;
        int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(param, "string", ApplicationLoader.applicationContext.getPackageName());
        return getString(param, resourceId);
    }

    public static String formatPluralString(String key, int plural) {
        if (key == null || key.length() == 0 || getInstance().currentPluralRules == null) {
            return "LOC_ERR:" + key;
        }
        String param = getInstance().stringForQuantity(getInstance().currentPluralRules.quantityForNumber(plural));
        param = key + "_" + param;
        int resourceId = ApplicationLoader.applicationContext.getResources().getIdentifier(param, "string", ApplicationLoader.applicationContext.getPackageName());
        return formatString(param, resourceId, plural);
    }

    public static String formatString(String key, int res, Object... args) {
        try {
            String value = getInstance().localeValues.get(key);
            if (value == null) {
                value = ApplicationLoader.applicationContext.getString(res);
            }

            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, value, args);
            } else {
                return String.format(value, args);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + key;
        }
    }

    public static String formatTTLString(int ttl) {
        if (ttl < 60) {
            return LocaleController.formatPluralString("Seconds", ttl);
        } else if (ttl < 60 * 60) {
            return LocaleController.formatPluralString("Minutes", ttl / 60);
        } else if (ttl < 60 * 60 * 24) {
            return LocaleController.formatPluralString("Hours", ttl / 60 / 60);
        } else if (ttl < 60 * 60 * 24 * 7) {
            return LocaleController.formatPluralString("Days", ttl / 60 / 60 / 24);
        } else {
            int days = ttl / 60 / 60 / 24;
            if (ttl % 7 == 0) {
                return LocaleController.formatPluralString("Weeks", days / 7);
            } else {
                return String.format("%s %s", LocaleController.formatPluralString("Weeks", days / 7), LocaleController.formatPluralString("Days", days % 7));
            }
        }
    }

    public String formatCurrencyString(long amount, String type) {
        type = type.toUpperCase();
        String customFormat;
        double doubleAmount;
        boolean discount = amount < 0;
        amount = Math.abs(amount);
        switch (type) {
            case "CLF":
                customFormat = " %.4f";
                doubleAmount = amount / 10000.0;
                break;

            case "BHD":
            case "IQD":
            case "JOD":
            case "KWD":
            case "LYD":
            case "OMR":
            case "TND":
                customFormat = " %.3f";
                doubleAmount = amount / 1000.0;
                break;

            case "BIF":
            case "BYR":
            case "CLP":
            case "CVE":
            case "DJF":
            case "GNF":
            case "ISK":
            case "JPY":
            case "KMF":
            case "KRW":
            case "MGA":
            case "PYG":
            case "RWF":
            case "UGX":
            case "UYI":
            case "VND":
            case "VUV":
            case "XAF":
            case "XOF":
            case "XPF":
                customFormat = " %.0f";
                doubleAmount = amount;
                break;

            case "MRO":
                customFormat = " %.1f";
                doubleAmount = amount / 10.0;
                break;

            default:
                customFormat = " %.2f";
                doubleAmount = amount / 100.0;
                break;
        }
        Currency ??urrency = Currency.getInstance(type);
        if (??urrency != null) {
            NumberFormat format = NumberFormat.getCurrencyInstance(currentLocale != null ? currentLocale : systemDefaultLocale);
            format.setCurrency(??urrency);
            return (discount ? "-" : "") + format.format(doubleAmount);
        }
        return (discount ? "-" : "") + String.format(Locale.US, type + customFormat, doubleAmount);
    }

    public String formatCurrencyDecimalString(long amount, String type, boolean inludeType) {
        type = type.toUpperCase();
        String customFormat;
        double doubleAmount;
        amount = Math.abs(amount);
        switch (type) {
            case "CLF":
                customFormat = " %.4f";
                doubleAmount = amount / 10000.0;
                break;

            case "BHD":
            case "IQD":
            case "JOD":
            case "KWD":
            case "LYD":
            case "OMR":
            case "TND":
                customFormat = " %.3f";
                doubleAmount = amount / 1000.0;
                break;

            case "BIF":
            case "BYR":
            case "CLP":
            case "CVE":
            case "DJF":
            case "GNF":
            case "ISK":
            case "JPY":
            case "KMF":
            case "KRW":
            case "MGA":
            case "PYG":
            case "RWF":
            case "UGX":
            case "UYI":
            case "VND":
            case "VUV":
            case "XAF":
            case "XOF":
            case "XPF":
                customFormat = " %.0f";
                doubleAmount = amount;
                break;

            case "MRO":
                customFormat = " %.1f";
                doubleAmount = amount / 10.0;
                break;

            default:
                customFormat = " %.2f";
                doubleAmount = amount / 100.0;
                break;
        }
        return String.format(Locale.US, inludeType ? type : "" + customFormat, doubleAmount).trim();
    }

    public static String formatStringSimple(String string, Object... args) {
        try {
            if (getInstance().currentLocale != null) {
                return String.format(getInstance().currentLocale, string, args);
            } else {
                return String.format(string, args);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "LOC_ERR: " + string;
        }
    }

    public static String formatCallDuration(int duration) {
        if (duration > 3600) {
            String result = LocaleController.formatPluralString("Hours", duration / 3600);
            int minutes = duration % 3600 / 60;
            if (minutes > 0) {
                result += ", " + LocaleController.formatPluralString("Minutes", minutes);
            }
            return result;
        } else if (duration > 60) {
            return LocaleController.formatPluralString("Minutes", duration / 60);
        } else {
            return LocaleController.formatPluralString("Seconds", duration);
        }
    }

    public void onDeviceConfigurationChange(Configuration newConfig) {
        if (changingConfiguration) {
            return;
        }
        is24HourFormat = DateFormat.is24HourFormat(ApplicationLoader.applicationContext);
        systemDefaultLocale = newConfig.locale;
        if (languageOverride != null) {
            LocaleInfo toSet = currentLocaleInfo;
            currentLocaleInfo = null;
            applyLanguage(toSet, false, false);
        } else {
            Locale newLocale = newConfig.locale;
            if (newLocale != null) {
                String d1 = newLocale.getDisplayName();
                String d2 = currentLocale.getDisplayName();
                if (d1 != null && d2 != null && !d1.equals(d2)) {
                    recreateFormatters();
                }
                currentLocale = newLocale;
                currentPluralRules = allRules.get(currentLocale.getLanguage());
                if (currentPluralRules == null) {
                    currentPluralRules = allRules.get("en");
                }
            }
        }
    }

    public static String formatDateChat(long date) {
        try {
            Calendar rightNow = Calendar.getInstance();
            date *= 1000;

            rightNow.setTimeInMillis(date);

            if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().chatDate.format(date);
            }
            return getInstance().chatFullDate.format(date);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR: formatDateChat";
    }

    public static String formatDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return getInstance().formatterDay.format(new Date(date));
            } else if (dateDay + 1 == day && year == dateYear) {
                return getString("Yesterday", R.string.Yesterday);
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return getInstance().formatterMonth.format(new Date(date));
            } else {
                return getInstance().formatterYear.format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR: formatDate";
    }

    public static String formatDateAudio(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return String.format("%s %s", LocaleController.getString("TodayAt", R.string.TodayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (dateDay + 1 == day && year == dateYear) {
                return String.format("%s %s", LocaleController.getString("YesterdayAt", R.string.YesterdayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatDateCallLog(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return getInstance().formatterDay.format(new Date(date));
            } else if (dateDay + 1 == day && year == dateYear) {
                return String.format("%s %s", LocaleController.getString("YesterdayAt", R.string.YesterdayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            } else {
                return LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().chatFullDate.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatLocationUpdateDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                int diff = (int) (ConnectionsManager.getInstance().getCurrentTime() - date / 1000) / 60;
                if (diff < 1) {
                    return LocaleController.getString("LocationUpdatedJustNow", R.string.LocationUpdatedJustNow);
                } else if (diff < 60) {
                    return LocaleController.formatPluralString("UpdatedMinutes", diff);
                }
                return String.format("%s %s %s", LocaleController.getString("LocationUpdated", R.string.LocationUpdated), LocaleController.getString("TodayAt", R.string.TodayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (dateDay + 1 == day && year == dateYear) {
                return String.format("%s %s %s", LocaleController.getString("LocationUpdated", R.string.LocationUpdated), LocaleController.getString("YesterdayAt", R.string.YesterdayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return String.format("%s %s", LocaleController.getString("LocationUpdated", R.string.LocationUpdated), format);
            } else {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return String.format("%s %s", LocaleController.getString("LocationUpdated", R.string.LocationUpdated), format);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatLocationLeftTime(int time) {
        String text;
        int hours = time / 60 / 60;
        time -= hours * 60 * 60;
        int minutes = time / 60;
        time -= minutes * 60;
        if (hours != 0) {
            text = String.format("%dh", hours + (minutes > 30 ? 1 : 0));
        } else if (minutes != 0) {
            text = String.format("%d", minutes + (time > 30 ? 1 : 0));
        } else {
            text = String.format("%d", time);
        }
        return text;
    }

    public static String formatDateOnline(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                return String.format("%s %s %s", LocaleController.getString("LastSeen", R.string.LastSeen), LocaleController.getString("TodayAt", R.string.TodayAt), getInstance().formatterDay.format(new Date(date)));
                /*int diff = (int) (ConnectionsManager.getInstance().getCurrentTime() - date) / 60;
                if (diff < 1) {
                    return LocaleController.getString("LastSeenNow", R.string.LastSeenNow);
                } else if (diff < 60) {
                    return LocaleController.formatPluralString("LastSeenMinutes", diff);
                } else {
                    return LocaleController.formatPluralString("LastSeenHours", (int) Math.ceil(diff / 60.0f));
                }*/
            } else if (dateDay + 1 == day && year == dateYear) {
                return String.format("%s %s %s", LocaleController.getString("LastSeen", R.string.LastSeen), LocaleController.getString("YesterdayAt", R.string.YesterdayAt), getInstance().formatterDay.format(new Date(date)));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterMonth.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return String.format("%s %s", LocaleController.getString("LastSeenDate", R.string.LastSeenDate), format);
            } else {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, getInstance().formatterYear.format(new Date(date)), getInstance().formatterDay.format(new Date(date)));
                return String.format("%s %s", LocaleController.getString("LastSeenDate", R.string.LastSeenDate), format);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    private FastDateFormat createFormatter(Locale locale, String format, String defaultFormat) {
        if (format == null || format.length() == 0) {
            format = defaultFormat;
        }
        FastDateFormat formatter;
        try {
            formatter = FastDateFormat.getInstance(format, locale);
        } catch (Exception e) {
            format = defaultFormat;
            formatter = FastDateFormat.getInstance(format, locale);
        }
        return formatter;
    }

    public void recreateFormatters() {
        Locale locale = currentLocale;
        if (locale == null) {
            locale = Locale.getDefault();
        }
        String lang = locale.getLanguage();
        if (lang == null) {
            lang = "en";
        }
        lang = lang.toLowerCase();
        isRTL = lang.startsWith("ar") || BuildVars.DEBUG_VERSION && (lang.startsWith("he") || lang.startsWith("iw") || lang.startsWith("fa"));
        nameDisplayOrder = lang.equals("ko") ? 2 : 1;

        formatterMonth = createFormatter(locale, getStringInternal("formatterMonth", R.string.formatterMonth), "dd MMM");
        formatterYear = createFormatter(locale, getStringInternal("formatterYear", R.string.formatterYear), "dd.MM.yy");
        formatterYearMax = createFormatter(locale, getStringInternal("formatterYearMax", R.string.formatterYearMax), "dd.MM.yyyy");
        chatDate = createFormatter(locale, getStringInternal("chatDate", R.string.chatDate), "d MMMM");
        chatFullDate = createFormatter(locale, getStringInternal("chatFullDate", R.string.chatFullDate), "d MMMM yyyy");
        formatterWeek = createFormatter(locale, getStringInternal("formatterWeek", R.string.formatterWeek), "EEE");
        formatterMonthYear = createFormatter(locale, getStringInternal("formatterMonthYear", R.string.formatterMonthYear), "MMMM yyyy");
        formatterDay = createFormatter(lang.toLowerCase().equals("ar") || lang.toLowerCase().equals("ko") ? locale : Locale.US, is24HourFormat ? getStringInternal("formatterDay24H", R.string.formatterDay24H) : getStringInternal("formatterDay12H", R.string.formatterDay12H), is24HourFormat ? "HH:mm" : "h:mm a");
        formatterStats = createFormatter(locale, is24HourFormat ? getStringInternal("formatterStats24H", R.string.formatterStats24H) : getStringInternal("formatterStats12H", R.string.formatterStats12H), is24HourFormat ? "MMM dd yyyy, HH:mm" : "MMM dd yyyy, h:mm a");
        formatterBannedUntil = createFormatter(locale, is24HourFormat ? getStringInternal("formatterBannedUntil24H", R.string.formatterBannedUntil24H) : getStringInternal("formatterBannedUntil12H", R.string.formatterBannedUntil12H), is24HourFormat ? "MMM dd yyyy, HH:mm" : "MMM dd yyyy, h:mm a");
        formatterBannedUntilThisYear = createFormatter(locale, is24HourFormat ? getStringInternal("formatterBannedUntilThisYear24H", R.string.formatterBannedUntilThisYear24H) : getStringInternal("formatterBannedUntilThisYear12H", R.string.formatterBannedUntilThisYear12H), is24HourFormat ? "MMM dd, HH:mm" : "MMM dd, h:mm a");
    }

    public static boolean isRTLCharacter(char ch) {
        return Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING || Character.getDirectionality(ch) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE;
    }

    public static String formatDateForBan(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (year == dateYear) {
                return getInstance().formatterBannedUntilThisYear.format(new Date(date));
            } else {
                return getInstance().formatterBannedUntil.format(new Date(date));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String stringForMessageListDate(long date) {
        try {
            date *= 1000;
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);

            if (Math.abs(System.currentTimeMillis() - date) >= 31536000000L) {
                return getInstance().formatterYear.format(new Date(date));
            } else {
                int dayDiff = dateDay - day;
                if (dayDiff == 0 || dayDiff == -1 && System.currentTimeMillis() - date < 60 * 60 * 8 * 1000) {
                    return getInstance().formatterDay.format(new Date(date));
                } else if (dayDiff > -7 && dayDiff <= -1) {
                    return getInstance().formatterWeek.format(new Date(date));
                } else {
                    return getInstance().formatterMonth.format(new Date(date));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    public static String formatShortNumber(int number, int[] rounded) {
        StringBuilder K = new StringBuilder();
        int lastDec = 0;
        int KCount = 0;
        while (number / 1000 > 0) {
            K.append("K");
            lastDec = (number % 1000) / 100;
            number /= 1000;
        }
        if (rounded != null) {
            double value = number + lastDec / 10.0;
            for (int a = 0; a < K.length(); a++) {
                value *= 1000;
            }
            rounded[0] = (int) value;
        }
        if (lastDec != 0 && K.length() > 0) {
            if (K.length() == 2) {
                return String.format(Locale.US, "%d.%dM", number, lastDec);
            } else {
                return String.format(Locale.US, "%d.%d%s", number, lastDec, K.toString());
            }
        }
        if (K.length() == 2) {
            return String.format(Locale.US, "%dM", number);
        } else {
            return String.format(Locale.US, "%d%s", number, K.toString());
        }
    }

    public static String formatUserStatus(TLRPC.User user) {
        if (user != null && user.status != null && user.status.expires == 0) {
            if (user.status instanceof TLRPC.TL_userStatusRecently) {
                user.status.expires = -100;
            } else if (user.status instanceof TLRPC.TL_userStatusLastWeek) {
                user.status.expires = -101;
            } else if (user.status instanceof TLRPC.TL_userStatusLastMonth) {
                user.status.expires = -102;
            }
        }
        if (user != null && user.status != null && user.status.expires <= 0) {
            if (MessagesController.getInstance().onlinePrivacy.containsKey(user.id)) {
                return getString("Online", R.string.Online);
            }
        }
        if (user == null || user.status == null || user.status.expires == 0 || UserObject.isDeleted(user) || user instanceof TLRPC.TL_userEmpty) {
            return getString("ALongTimeAgo", R.string.ALongTimeAgo);
        } else {
            int currentTime = ConnectionsManager.getInstance().getCurrentTime();
            if (user.status.expires > currentTime) {
                return getString("Online", R.string.Online);
            } else {
                if (user.status.expires == -1) {
                    return getString("Invisible", R.string.Invisible);
                } else if (user.status.expires == -100) {
                    return getString("Lately", R.string.Lately);
                } else if (user.status.expires == -101) {
                    return getString("WithinAWeek", R.string.WithinAWeek);
                } else if (user.status.expires == -102) {
                    return getString("WithinAMonth", R.string.WithinAMonth);
                }  else {
                    return formatDateOnline(user.status.expires);
                }
            }
        }
    }

    private String escapeString(String str) {
        if (str.contains("[CDATA")) {
            return str;
        }
        return str.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;");
    }

    public void saveRemoteLocaleStrings(final TLRPC.TL_langPackDifference difference) {
        if (difference == null || difference.strings.isEmpty()) {
            return;
        }
        final String langCode = difference.lang_code.replace('-', '_').toLowerCase();
        File finalFile = new File(ApplicationLoader.getFilesDirFixed(), "remote_" + langCode + ".xml");
        try {
            final HashMap<String, String> values;
            if (difference.from_version == 0) {
                values = new HashMap<>();
            } else {
                values = getLocaleFileStrings(finalFile, true);
            }
            for (int a = 0; a < difference.strings.size(); a++) {
                TLRPC.LangPackString string = difference.strings.get(a);
                if (string instanceof TLRPC.TL_langPackString) {
                    values.put(string.key, escapeString(string.value));
                } else if (string instanceof TLRPC.TL_langPackStringPluralized) {
                    values.put(string.key + "_zero", string.zero_value != null ? escapeString(string.zero_value) : "");
                    values.put(string.key + "_one", string.one_value != null ? escapeString(string.one_value) : "");
                    values.put(string.key + "_two", string.two_value != null ? escapeString(string.two_value) : "");
                    values.put(string.key + "_few", string.few_value != null ? escapeString(string.few_value) : "");
                    values.put(string.key + "_many", string.many_value != null ? escapeString(string.many_value) : "");
                    values.put(string.key + "_other", string.other_value != null ? escapeString(string.other_value) : "");
                } else if (string instanceof TLRPC.TL_langPackStringDeleted) {
                    values.remove(string.key);
                }
            }
            FileLog.d("save locale file to " + finalFile);
            BufferedWriter writer = new BufferedWriter(new FileWriter(finalFile));
            writer.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            writer.write("<resources>\n");
            for (HashMap.Entry<String, String> entry : values.entrySet()) {
                writer.write(String.format("<string name=\"%1$s\">%2$s</string>\n", entry.getKey(), entry.getValue()));
            }
            writer.write("</resources>");
            writer.close();
            final HashMap<String, String> valuesToSet = getLocaleFileStrings(finalFile);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    LocaleInfo localeInfo = getLanguageFromDict(langCode);
                    if (localeInfo != null) {
                        localeInfo.version = difference.version;
                    }
                    saveOtherLanguages();
                    if (currentLocaleInfo != null && currentLocaleInfo.isLocal()) {
                        return;
                    }
                    try {
                        Locale newLocale;
                        String[] args = localeInfo.shortName.split("_");
                        if (args.length == 1) {
                            newLocale = new Locale(localeInfo.shortName);
                        } else {
                            newLocale = new Locale(args[0], args[1]);
                        }
                        if (newLocale != null) {
                            languageOverride = localeInfo.shortName;

                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putString("language", localeInfo.getKey());
                            editor.commit();
                        }
                        if (newLocale != null) {
                            localeValues = valuesToSet;
                            currentLocale = newLocale;
                            currentLocaleInfo = localeInfo;
                            currentPluralRules = allRules.get(currentLocale.getLanguage());
                            if (currentPluralRules == null) {
                                currentPluralRules = allRules.get("en");
                            }
                            changingConfiguration = true;
                            Locale.setDefault(currentLocale);
                            android.content.res.Configuration config = new android.content.res.Configuration();
                            config.locale = currentLocale;
                            ApplicationLoader.applicationContext.getResources().updateConfiguration(config, ApplicationLoader.applicationContext.getResources().getDisplayMetrics());
                            changingConfiguration = false;
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                        changingConfiguration = false;
                    }
                    recreateFormatters();
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInterface);
                }
            });
        } catch (Exception ignore) {

        }
    }

    public void loadRemoteLanguages() {
        if (loadingRemoteLanguages) {
            return;
        }
        loadingRemoteLanguages = true;
        TLRPC.TL_langpack_getLanguages req = new TLRPC.TL_langpack_getLanguages();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingRemoteLanguages = false;
                            TLRPC.Vector res = (TLRPC.Vector) response;
                            HashMap<String, LocaleInfo> remoteLoaded = new HashMap<>();
                            remoteLanguages.clear();
                            for (int a = 0; a < res.objects.size(); a++) {
                                TLRPC.TL_langPackLanguage language = (TLRPC.TL_langPackLanguage) res.objects.get(a);
                                FileLog.d("loaded lang " + language.name);
                                LocaleInfo localeInfo = new LocaleInfo();
                                localeInfo.nameEnglish = language.name;
                                localeInfo.name = language.native_name;
                                localeInfo.shortName = language.lang_code.replace('-', '_').toLowerCase();
                                localeInfo.pathToFile = "remote";

                                LocaleInfo existing = getLanguageFromDict(localeInfo.getKey());
                                if (existing == null) {
                                    languages.add(localeInfo);
                                    languagesDict.put(localeInfo.getKey(), localeInfo);
                                    existing = localeInfo;
                                } else {
                                    existing.nameEnglish = localeInfo.nameEnglish;
                                    existing.name = localeInfo.name;
                                    existing.pathToFile = localeInfo.pathToFile;
                                }
                                remoteLanguages.add(localeInfo);
                                remoteLoaded.put(localeInfo.getKey(), existing);
                            }
                            for (int a = 0; a < languages.size(); a++) {
                                LocaleInfo info = languages.get(a);
                                if (info.isBuiltIn() || !info.isRemote()) {
                                    continue;
                                }
                                LocaleInfo existing = remoteLoaded.get(info.getKey());
                                if (existing == null) {
                                    FileLog.d("remove lang " + info.getKey());
                                    languages.remove(a);
                                    languagesDict.remove(info.getKey());
                                    a--;
                                    if (info == currentLocaleInfo) {
                                        if (systemDefaultLocale.getLanguage() != null) {
                                            info = getLanguageFromDict(systemDefaultLocale.getLanguage());
                                        }
                                        if (info == null) {
                                            info = getLanguageFromDict(getLocaleString(systemDefaultLocale));
                                        }
                                        if (info == null) {
                                            info = getLanguageFromDict("en");
                                        }
                                        applyLanguage(info, true, false);
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.reloadInterface);
                                    }
                                }
                            }
                            saveOtherLanguages();
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.suggestedLangpack);
                            applyLanguage(currentLocaleInfo, true, false);
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagWithoutLogin);
    }

    private void applyRemoteLanguage(LocaleInfo localeInfo, TLRPC.TL_langPackLanguage language, boolean force) {
        if (localeInfo == null && language == null || localeInfo != null && !localeInfo.isRemote()) {
            return;
        }
        if (localeInfo.version != 0 && !force) {
            TLRPC.TL_langpack_getDifference req = new TLRPC.TL_langpack_getDifference();
            req.from_version = localeInfo.version;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                saveRemoteLocaleStrings((TLRPC.TL_langPackDifference) response);
                            }
                        });
                    }
                }
            }, ConnectionsManager.RequestFlagWithoutLogin);
        } else {
            ConnectionsManager.getInstance().setLangCode(localeInfo != null ? localeInfo.shortName : language.lang_code);
            TLRPC.TL_langpack_getLangPack req = new TLRPC.TL_langpack_getLangPack();
            if (language == null) {
                req.lang_code = localeInfo.shortName;
            } else {
                req.lang_code = language.lang_code;
            }
            req.lang_code = req.lang_code.replace("_", "-");
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    if (response != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                saveRemoteLocaleStrings((TLRPC.TL_langPackDifference) response);
                            }
                        });
                    }
                }
            }, ConnectionsManager.RequestFlagWithoutLogin);
        }
    }

    public String getTranslitString(String src) {
        if (translitChars == null) {
            translitChars = new HashMap<>(520);
            translitChars.put("??", "c");
            translitChars.put("???", "n");
            translitChars.put("??", "d");
            translitChars.put("???", "y");
            translitChars.put("???", "o");
            translitChars.put("??", "o");
            translitChars.put("???", "a");
            translitChars.put("??", "h");
            translitChars.put("??", "y");
            translitChars.put("??", "k");
            translitChars.put("???", "u");
            translitChars.put("???", "aa");
            translitChars.put("??", "ij");
            translitChars.put("???", "l");
            translitChars.put("??", "i");
            translitChars.put("???", "b");
            translitChars.put("??", "r");
            translitChars.put("??", "e");
            translitChars.put("???", "ffi");
            translitChars.put("??", "o");
            translitChars.put("???", "r");
            translitChars.put("???", "o");
            translitChars.put("??", "i");
            translitChars.put("???", "p");
            translitChars.put("??", "y");
            translitChars.put("???", "e");
            translitChars.put("???", "o");
            translitChars.put("???", "a");
            translitChars.put("??", "b");
            translitChars.put("???", "e");
            translitChars.put("??", "c");
            translitChars.put("??", "h");
            translitChars.put("???", "b");
            translitChars.put("???", "s");
            translitChars.put("??", "d");
            translitChars.put("???", "o");
            translitChars.put("??", "j");
            translitChars.put("???", "a");
            translitChars.put("??", "y");
            translitChars.put("??", "l");
            translitChars.put("??", "v");
            translitChars.put("???", "p");
            translitChars.put("???", "fi");
            translitChars.put("???", "k");
            translitChars.put("???", "d");
            translitChars.put("???", "l");
            translitChars.put("??", "e");
            translitChars.put("??", "yo");
            translitChars.put("???", "k");
            translitChars.put("??", "c");
            translitChars.put("??", "r");
            translitChars.put("??", "hv");
            translitChars.put("??", "b");
            translitChars.put("???", "o");
            translitChars.put("??", "ou");
            translitChars.put("??", "j");
            translitChars.put("???", "g");
            translitChars.put("???", "n");
            translitChars.put("??", "j");
            translitChars.put("??", "g");
            translitChars.put("??", "dz");
            translitChars.put("??", "z");
            translitChars.put("???", "au");
            translitChars.put("??", "u");
            translitChars.put("???", "g");
            translitChars.put("??", "o");
            translitChars.put("??", "a");
            translitChars.put("??", "a");
            translitChars.put("??", "o");
            translitChars.put("??", "r");
            translitChars.put("???", "o");
            translitChars.put("??", "a");
            translitChars.put("??", "l");
            translitChars.put("??", "s");
            translitChars.put("???", "fl");
            translitChars.put("??", "i");
            translitChars.put("???", "e");
            translitChars.put("???", "n");
            translitChars.put("??", "i");
            translitChars.put("??", "n");
            translitChars.put("???", "i");
            translitChars.put("??", "t");
            translitChars.put("???", "z");
            translitChars.put("???", "y");
            translitChars.put("??", "y");
            translitChars.put("???", "s");
            translitChars.put("??", "r");
            translitChars.put("??", "g");
            translitChars.put("??", "v");
            translitChars.put("???", "u");
            translitChars.put("???", "k");
            translitChars.put("???", "et");
            translitChars.put("??", "i");
            translitChars.put("??", "t");
            translitChars.put("???", "c");
            translitChars.put("??", "l");
            translitChars.put("???", "av");
            translitChars.put("??", "u");
            translitChars.put("??", "ae");
            translitChars.put("??", "i");
            translitChars.put("??", "a");
            translitChars.put("??", "u");
            translitChars.put("???", "s");
            translitChars.put("???", "r");
            translitChars.put("???", "a");
            translitChars.put("??", "b");
            translitChars.put("???", "h");
            translitChars.put("???", "s");
            translitChars.put("???", "e");
            translitChars.put("??", "h");
            translitChars.put("???", "x");
            translitChars.put("???", "k");
            translitChars.put("???", "d");
            translitChars.put("??", "oi");
            translitChars.put("???", "p");
            translitChars.put("??", "h");
            translitChars.put("???", "v");
            translitChars.put("???", "w");
            translitChars.put("??", "n");
            translitChars.put("??", "m");
            translitChars.put("??", "g");
            translitChars.put("??", "n");
            translitChars.put("???", "p");
            translitChars.put("???", "v");
            translitChars.put("??", "u");
            translitChars.put("???", "b");
            translitChars.put("???", "p");
            translitChars.put("??", "");
            translitChars.put("??", "a");
            translitChars.put("??", "c");
            translitChars.put("???", "o");
            translitChars.put("???", "a");
            translitChars.put("??", "f");
            translitChars.put("??", "ae");
            translitChars.put("???", "vy");
            translitChars.put("???", "ff");
            translitChars.put("???", "r");
            translitChars.put("??", "o");
            translitChars.put("??", "o");
            translitChars.put("???", "u");
            translitChars.put("??", "z");
            translitChars.put("???", "f");
            translitChars.put("???", "d");
            translitChars.put("??", "e");
            translitChars.put("??", "u");
            translitChars.put("??", "p");
            translitChars.put("??", "n");
            translitChars.put("??", "q");
            translitChars.put("???", "a");
            translitChars.put("??", "k");
            translitChars.put("??", "i");
            translitChars.put("???", "u");
            translitChars.put("??", "t");
            translitChars.put("??", "r");
            translitChars.put("??", "k");
            translitChars.put("???", "t");
            translitChars.put("???", "q");
            translitChars.put("???", "a");
            translitChars.put("??", "n");
            translitChars.put("??", "j");
            translitChars.put("??", "l");
            translitChars.put("???", "f");
            translitChars.put("??", "d");
            translitChars.put("???", "s");
            translitChars.put("???", "r");
            translitChars.put("???", "v");
            translitChars.put("??", "o");
            translitChars.put("???", "c");
            translitChars.put("???", "u");
            translitChars.put("???", "z");
            translitChars.put("???", "u");
            translitChars.put("??", "n");
            translitChars.put("??", "w");
            translitChars.put("???", "a");
            translitChars.put("??", "lj");
            translitChars.put("??", "b");
            translitChars.put("??", "r");
            translitChars.put("??", "o");
            translitChars.put("???", "w");
            translitChars.put("??", "d");
            translitChars.put("???", "ay");
            translitChars.put("??", "u");
            translitChars.put("???", "b");
            translitChars.put("??", "u");
            translitChars.put("???", "e");
            translitChars.put("??", "a");
            translitChars.put("??", "h");
            translitChars.put("???", "o");
            translitChars.put("??", "u");
            translitChars.put("??", "y");
            translitChars.put("??", "o");
            translitChars.put("???", "e");
            translitChars.put("???", "e");
            translitChars.put("??", "i");
            translitChars.put("???", "e");
            translitChars.put("???", "t");
            translitChars.put("???", "d");
            translitChars.put("???", "h");
            translitChars.put("???", "s");
            translitChars.put("??", "e");
            translitChars.put("???", "m");
            translitChars.put("??", "o");
            translitChars.put("??", "e");
            translitChars.put("??", "i");
            translitChars.put("??", "d");
            translitChars.put("???", "m");
            translitChars.put("???", "y");
            translitChars.put("??", "ya");
            translitChars.put("??", "w");
            translitChars.put("???", "e");
            translitChars.put("???", "u");
            translitChars.put("??", "z");
            translitChars.put("??", "j");
            translitChars.put("???", "d");
            translitChars.put("??", "u");
            translitChars.put("??", "j");
            translitChars.put("??", "zh");
            translitChars.put("??", "e");
            translitChars.put("??", "u");
            translitChars.put("??", "g");
            translitChars.put("???", "r");
            translitChars.put("??", "n");
            translitChars.put("??", "");
            translitChars.put("???", "e");
            translitChars.put("???", "s");
            translitChars.put("???", "d");
            translitChars.put("??", "k");
            translitChars.put("???", "ae");
            translitChars.put("??", "e");
            translitChars.put("???", "o");
            translitChars.put("???", "m");
            translitChars.put("???", "f");
            translitChars.put("??", "a");
            translitChars.put("???", "a");
            translitChars.put("???", "oo");
            translitChars.put("???", "m");
            translitChars.put("???", "p");
            translitChars.put("??", "ts");
            translitChars.put("???", "u");
            translitChars.put("???", "k");
            translitChars.put("???", "h");
            translitChars.put("??", "t");
            translitChars.put("???", "p");
            translitChars.put("???", "m");
            translitChars.put("??", "a");
            translitChars.put("???", "n");
            translitChars.put("???", "v");
            translitChars.put("??", "e");
            translitChars.put("???", "z");
            translitChars.put("???", "d");
            translitChars.put("???", "p");
            translitChars.put("??", "m");
            translitChars.put("??", "l");
            translitChars.put("???", "z");
            translitChars.put("??", "m");
            translitChars.put("???", "r");
            translitChars.put("???", "v");
            translitChars.put("??", "u");
            translitChars.put("??", "ss");
            translitChars.put("??", "t");
            translitChars.put("??", "h");
            translitChars.put("???", "t");
            translitChars.put("??", "z");
            translitChars.put("???", "r");
            translitChars.put("??", "n");
            translitChars.put("??", "a");
            translitChars.put("???", "y");
            translitChars.put("???", "y");
            translitChars.put("???", "oe");
            translitChars.put("??", "i");
            translitChars.put("???", "x");
            translitChars.put("??", "u");
            translitChars.put("???", "j");
            translitChars.put("???", "a");
            translitChars.put("??", "z");
            translitChars.put("???", "s");
            translitChars.put("???", "i");
            translitChars.put("???", "ao");
            translitChars.put("??", "z");
            translitChars.put("??", "y");
            translitChars.put("??", "e");
            translitChars.put("??", "o");
            translitChars.put("???", "d");
            translitChars.put("???", "l");
            translitChars.put("??", "u");
            translitChars.put("???", "a");
            translitChars.put("???", "b");
            translitChars.put("???", "u");
            translitChars.put("??", "k");
            translitChars.put("???", "a");
            translitChars.put("???", "t");
            translitChars.put("??", "y");
            translitChars.put("???", "t");
            translitChars.put("??", "z");
            translitChars.put("???", "l");
            translitChars.put("??", "j");
            translitChars.put("???", "z");
            translitChars.put("???", "h");
            translitChars.put("???", "w");
            translitChars.put("???", "k");
            translitChars.put("???", "o");
            translitChars.put("??", "i");
            translitChars.put("??", "g");
            translitChars.put("??", "e");
            translitChars.put("??", "a");
            translitChars.put("???", "a");
            translitChars.put("??", "sch");
            translitChars.put("??", "q");
            translitChars.put("???", "t");
            translitChars.put("???", "um");
            translitChars.put("???", "c");
            translitChars.put("???", "x");
            translitChars.put("???", "u");
            translitChars.put("???", "i");
            translitChars.put("???", "r");
            translitChars.put("??", "s");
            translitChars.put("???", "o");
            translitChars.put("???", "y");
            translitChars.put("???", "s");
            translitChars.put("??", "nj");
            translitChars.put("??", "a");
            translitChars.put("???", "t");
            translitChars.put("??", "l");
            translitChars.put("??", "z");
            translitChars.put("???", "th");
            translitChars.put("??", "d");
            translitChars.put("??", "s");
            translitChars.put("??", "s");
            translitChars.put("???", "u");
            translitChars.put("???", "e");
            translitChars.put("???", "s");
            translitChars.put("??", "e");
            translitChars.put("???", "u");
            translitChars.put("???", "o");
            translitChars.put("??", "s");
            translitChars.put("???", "v");
            translitChars.put("???", "is");
            translitChars.put("???", "o");
            translitChars.put("??", "e");
            translitChars.put("??", "a");
            translitChars.put("???", "ffl");
            translitChars.put("???", "o");
            translitChars.put("??", "i");
            translitChars.put("???", "ue");
            translitChars.put("??", "d");
            translitChars.put("???", "z");
            translitChars.put("???", "w");
            translitChars.put("???", "a");
            translitChars.put("???", "t");
            translitChars.put("??", "g");
            translitChars.put("??", "n");
            translitChars.put("??", "g");
            translitChars.put("???", "u");
            translitChars.put("??", "f");
            translitChars.put("???", "a");
            translitChars.put("???", "n");
            translitChars.put("??", "i");
            translitChars.put("???", "r");
            translitChars.put("??", "a");
            translitChars.put("??", "s");
            translitChars.put("??", "u");
            translitChars.put("??", "o");
            translitChars.put("??", "r");
            translitChars.put("??", "t");
            translitChars.put("???", "i");
            translitChars.put("??", "ae");
            translitChars.put("???", "v");
            translitChars.put("??", "oe");
            translitChars.put("???", "m");
            translitChars.put("??", "z");
            translitChars.put("??", "e");
            translitChars.put("???", "av");
            translitChars.put("???", "o");
            translitChars.put("???", "e");
            translitChars.put("??", "l");
            translitChars.put("???", "i");
            translitChars.put("???", "d");
            translitChars.put("???", "st");
            translitChars.put("???", "l");
            translitChars.put("??", "r");
            translitChars.put("???", "ou");
            translitChars.put("??", "t");
            translitChars.put("??", "a");
            translitChars.put("??", "e");
            translitChars.put("???", "e");
            translitChars.put("???", "o");
            translitChars.put("??", "c");
            translitChars.put("???", "s");
            translitChars.put("???", "a");
            translitChars.put("??", "u");
            translitChars.put("???", "a");
            translitChars.put("??", "g");
            translitChars.put("??", "r");
            translitChars.put("???", "k");
            translitChars.put("???", "z");
            translitChars.put("??", "s");
            translitChars.put("???", "e");
            translitChars.put("??", "g");
            translitChars.put("???", "l");
            translitChars.put("???", "f");
            translitChars.put("???", "x");
            translitChars.put("??", "h");
            translitChars.put("??", "o");
            translitChars.put("??", "e");
            translitChars.put("???", "o");
            translitChars.put("??", "t");
            translitChars.put("??", "o");
            translitChars.put("i??", "i");
            translitChars.put("???", "n");
            translitChars.put("??", "c");
            translitChars.put("???", "g");
            translitChars.put("???", "w");
            translitChars.put("???", "d");
            translitChars.put("???", "l");
            translitChars.put("??", "ch");
            translitChars.put("??", "oe");
            translitChars.put("???", "r");
            translitChars.put("??", "l");
            translitChars.put("??", "r");
            translitChars.put("??", "o");
            translitChars.put("???", "n");
            translitChars.put("???", "ae");
            translitChars.put("??", "l");
            translitChars.put("??", "a");
            translitChars.put("??", "p");
            translitChars.put("???", "o");
            translitChars.put("??", "i");
            translitChars.put("??", "r");
            translitChars.put("??", "dz");
            translitChars.put("???", "g");
            translitChars.put("???", "u");
            translitChars.put("??", "o");
            translitChars.put("??", "l");
            translitChars.put("???", "w");
            translitChars.put("??", "t");
            translitChars.put("??", "n");
            translitChars.put("??", "r");
            translitChars.put("??", "a");
            translitChars.put("??", "u");
            translitChars.put("???", "l");
            translitChars.put("???", "o");
            translitChars.put("???", "o");
            translitChars.put("???", "b");
            translitChars.put("??", "r");
            translitChars.put("???", "r");
            translitChars.put("??", "y");
            translitChars.put("???", "f");
            translitChars.put("???", "h");
            translitChars.put("??", "o");
            translitChars.put("??", "u");
            translitChars.put("???", "r");
            translitChars.put("??", "h");
            translitChars.put("??", "o");
            translitChars.put("??", "u");
            translitChars.put("???", "o");
            translitChars.put("???", "p");
            translitChars.put("???", "i");
            translitChars.put("???", "u");
            translitChars.put("??", "a");
            translitChars.put("???", "i");
            translitChars.put("???", "t");
            translitChars.put("???", "e");
            translitChars.put("???", "u");
            translitChars.put("??", "i");
            translitChars.put("??", "o");
            translitChars.put("??", "s");
            translitChars.put("??", "i");
            translitChars.put("??", "r");
            translitChars.put("??", "g");
            translitChars.put("??", "r");
            translitChars.put("???", "h");
            translitChars.put("??", "u");
            translitChars.put("??", "o");
            translitChars.put("??", "sh");
            translitChars.put("???", "l");
            translitChars.put("???", "h");
            translitChars.put("??", "t");
            translitChars.put("??", "n");
            translitChars.put("???", "e");
            translitChars.put("??", "i");
            translitChars.put("???", "w");
            translitChars.put("??", "b");
            translitChars.put("??", "e");
            translitChars.put("???", "e");
            translitChars.put("??", "l");
            translitChars.put("???", "o");
            translitChars.put("??", "l");
            translitChars.put("???", "y");
            translitChars.put("???", "j");
            translitChars.put("???", "k");
            translitChars.put("???", "v");
            translitChars.put("??", "e");
            translitChars.put("??", "a");
            translitChars.put("??", "s");
            translitChars.put("??", "r");
            translitChars.put("??", "v");
            translitChars.put("???", "a");
            translitChars.put("???", "c");
            translitChars.put("???", "e");
            translitChars.put("??", "m");
            translitChars.put("??", "e");
            translitChars.put("???", "w");
            translitChars.put("??", "o");
            translitChars.put("??", "c");
            translitChars.put("??", "g");
            translitChars.put("??", "c");
            translitChars.put("??", "yu");
            translitChars.put("???", "o");
            translitChars.put("???", "k");
            translitChars.put("???", "q");
            translitChars.put("??", "g");
            translitChars.put("???", "o");
            translitChars.put("???", "s");
            translitChars.put("???", "o");
            translitChars.put("??", "h");
            translitChars.put("??", "o");
            translitChars.put("???", "tz");
            translitChars.put("???", "e");
            translitChars.put("??", "o");
        }
        StringBuilder dst = new StringBuilder(src.length());
        int len = src.length();
        for (int a = 0; a < len; a++) {
            String ch = src.substring(a, a + 1);
            String tch = translitChars.get(ch);
            if (tch != null) {
                dst.append(tch);
            } else {
                dst.append(ch);
            }
        }
        return dst.toString();
    }

    abstract public static class PluralRules {
        abstract int quantityForNumber(int n);
    }

    public static class PluralRules_Zero extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0 || count == 1) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Welsh extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (count == 3) {
                return QUANTITY_FEW;
            } else if (count == 6) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Two extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Tachelhit extends PluralRules {
        public int quantityForNumber(int count) {
            if (count >= 0 && count <= 1) {
                return QUANTITY_ONE;
            } else if (count >= 2 && count <= 10) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Slovenian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (rem100 == 1) {
                return QUANTITY_ONE;
            } else if (rem100 == 2) {
                return QUANTITY_TWO;
            } else if (rem100 >= 3 && rem100 <= 4) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Romanian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if ((count == 0 || (rem100 >= 1 && rem100 <= 19))) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Polish extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14) && !(rem100 >= 22 && rem100 <= 24)) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_One extends PluralRules {
        public int quantityForNumber(int count) {
            return count == 1 ? QUANTITY_ONE : QUANTITY_OTHER;
        }
    }

    public static class PluralRules_None extends PluralRules {
        public int quantityForNumber(int count) {
            return QUANTITY_OTHER;
        }
    }

    public static class PluralRules_Maltese extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 0 || (rem100 >= 2 && rem100 <= 10)) {
                return QUANTITY_FEW;
            } else if (rem100 >= 11 && rem100 <= 19) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Macedonian extends PluralRules {
        public int quantityForNumber(int count) {
            if (count % 10 == 1 && count != 11) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Lithuanian extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (rem10 == 1 && !(rem100 >= 11 && rem100 <= 19)) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 9 && !(rem100 >= 11 && rem100 <= 19)) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Latvian extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count % 10 == 1 && count % 100 != 11) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Langi extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count > 0 && count < 2) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_French extends PluralRules {
        public int quantityForNumber(int count) {
            if (count >= 0 && count < 2) {
                return QUANTITY_ONE;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Czech extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 1) {
                return QUANTITY_ONE;
            } else if (count >= 2 && count <= 4) {
                return QUANTITY_FEW;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Breton extends PluralRules {
        public int quantityForNumber(int count) {
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (count == 3) {
                return QUANTITY_FEW;
            } else if (count == 6) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Balkan extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            int rem10 = count % 10;
            if (rem10 == 1 && rem100 != 11) {
                return QUANTITY_ONE;
            } else if (rem10 >= 2 && rem10 <= 4 && !(rem100 >= 12 && rem100 <= 14)) {
                return QUANTITY_FEW;
            } else if ((rem10 == 0 || (rem10 >= 5 && rem10 <= 9) || (rem100 >= 11 && rem100 <= 14))) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }

    public static class PluralRules_Arabic extends PluralRules {
        public int quantityForNumber(int count) {
            int rem100 = count % 100;
            if (count == 0) {
                return QUANTITY_ZERO;
            } else if (count == 1) {
                return QUANTITY_ONE;
            } else if (count == 2) {
                return QUANTITY_TWO;
            } else if (rem100 >= 3 && rem100 <= 10) {
                return QUANTITY_FEW;
            } else if (rem100 >= 11 && rem100 <= 99) {
                return QUANTITY_MANY;
            } else {
                return QUANTITY_OTHER;
            }
        }
    }
}

package com.anomaly.goldiesettings.model;

import android.content.Context;

import com.anomaly.goldiesettings.App;
import com.anomaly.goldiesettings.R;

/**
 * Voice / personality / relationship preset table. Reads from
 * {@code strings.xml} so we can edit the values without recompiling Java.
 */
public final class VoicePresets {

    private VoicePresets() {}

    public static String[] avatarList() { return arr(R.array.avatar_options); }
    public static int avatarCount() { return avatarList().length; }

    public static String[] voiceListFemale() { return arr(R.array.voice_female); }
    public static String[] voiceTypeFemale() { return arr(R.array.voice_female_type); }
    public static int voiceCountFemale() { return voiceListFemale().length; }

    public static String[] voiceListMale() { return arr(R.array.voice_male); }
    public static String[] voiceTypeMale() { return arr(R.array.voice_male_type); }
    public static int voiceCountMale() { return voiceListMale().length; }

    public static String[] personalityList() { return arr(R.array.personality); }
    public static String[] personalityPrompt() { return arr(R.array.personality_prompt); }
    public static int personalityCount() { return personalityList().length; }

    public static String[] relationshipListFemale() { return arr(R.array.relationship_female); }
    public static String[] relationshipPromptFemale() { return arr(R.array.relationship_female_prompt); }
    public static int relationshipCountFemale() { return relationshipListFemale().length; }

    public static String[] relationshipListMale() { return arr(R.array.relationship_male); }
    public static String[] relationshipPromptMale() { return arr(R.array.relationship_male_prompt); }
    public static int relationshipCountMale() { return relationshipListMale().length; }

    public static String[] relationshipList(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? relationshipListFemale() : relationshipListMale();
    }
    public static String[] relationshipPrompt(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? relationshipPromptFemale() : relationshipPromptMale();
    }
    public static int relationshipCount(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? relationshipCountFemale() : relationshipCountMale();
    }

    public static String[] voiceList(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? voiceListFemale() : voiceListMale();
    }
    public static String[] voiceType(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? voiceTypeFemale() : voiceTypeMale();
    }
    public static int voiceCount(Settings s) {
        return s.avatarIdx() == Settings.AVATAR_FEMALE ? voiceCountFemale() : voiceCountMale();
    }

    private static String[] arr(int id) {
        return App.get().getResources().getStringArray(id);
    }
}

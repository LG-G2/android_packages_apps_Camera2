/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera;

import java.util.Locale;

import android.content.res.Resources;
import android.hardware.Camera.Parameters;
import android.view.LayoutInflater;

import com.android.camera.ui.AbstractSettingPopup;
import com.android.camera.ui.CountdownTimerPopup;
import com.android.camera.ui.ListPrefSettingPopup;
import com.android.camera.ui.MoreSettingPopup;
import com.android.camera.ui.PieItem;
import com.android.camera.ui.PieItem.OnClickListener;
import com.android.camera.ui.PieRenderer;
import com.android.camera2.R;

public class PhotoMenu extends PieController
        implements MoreSettingPopup.Listener,
        CountdownTimerPopup.Listener,
        ListPrefSettingPopup.Listener {
    private static String TAG = "PhotoMenu";

    private final String mSettingOff;

    private PhotoUI mUI;
    private String[] mOtherKeys;
    private AbstractSettingPopup mPopup;

    private static final int POPUP_NONE = 0;
    private static final int POPUP_FIRST_LEVEL = 1;
    private static final int POPUP_SECOND_LEVEL = 2;
    private int mPopupStatus;
    private CameraActivity mActivity;

    public PhotoMenu(CameraActivity activity, PhotoUI ui, PieRenderer pie) {
        super(activity, pie);
        mUI = ui;
        mSettingOff = activity.getString(R.string.setting_off_value);
        mActivity = activity;
    }

    public void initialize(PreferenceGroup group) {
        super.initialize(group);
        mPopup = null;
        mPopupStatus = POPUP_NONE;
        PieItem item = null;
        final Resources res = mActivity.getResources();
        Locale locale = res.getConfiguration().locale;
        // The order is from left to right in the menu.

        // HDR+ (GCam).
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR_PLUS) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_HDR_PLUS, true);
            mRenderer.addItem(item);
        }

        // HDR.
        if (group.findPreference(CameraSettings.KEY_CAMERA_HDR) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_HDR, true);
            mRenderer.addItem(item);
        }
        // Exposure compensation.
        if (group.findPreference(CameraSettings.KEY_EXPOSURE) != null) {
            item = makeItem(CameraSettings.KEY_EXPOSURE);
            item.setLabel(res.getString(R.string.pref_exposure_label));
            mRenderer.addItem(item);
        }
        // More settings.
        PieItem more = makeItem(R.drawable.ic_settings_holo_light);
        more.setLabel(res.getString(R.string.camera_menu_more_label));
        mRenderer.addItem(more);

        // Flash.
        if (group.findPreference(CameraSettings.KEY_FLASH_MODE) != null) {
            item = makeItem(CameraSettings.KEY_FLASH_MODE);
            item.setLabel(res.getString(R.string.pref_camera_flashmode_label));
            mRenderer.addItem(item);
        }
        // Camera switcher.
        if (group.findPreference(CameraSettings.KEY_CAMERA_ID) != null) {
            item = makeSwitchItem(CameraSettings.KEY_CAMERA_ID, false);
            final PieItem fitem = item;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    // Find the index of next camera.
                    ListPreference pref = mPreferenceGroup
                            .findPreference(CameraSettings.KEY_CAMERA_ID);
                    if (pref != null) {
                        int index = pref.findIndexOfValue(pref.getValue());
                        CharSequence[] values = pref.getEntryValues();
                        index = (index + 1) % values.length;
                        pref.setValueIndex(index);
                        mListener.onCameraPickerClicked(index);
                    }
                    updateItem(fitem, CameraSettings.KEY_CAMERA_ID);
                }
            });
            mRenderer.addItem(item);
        }
        // Location.
        if (group.findPreference(CameraSettings.KEY_RECORD_LOCATION) != null) {
            item = makeSwitchItem(CameraSettings.KEY_RECORD_LOCATION, true);
            more.addItem(item);
            if (mActivity.isSecureCamera()) {
                // Prevent location preference from getting changed in secure camera mode
                item.setEnabled(false);
            }
        }
        // Countdown timer.
        final ListPreference ctpref = group.findPreference(CameraSettings.KEY_TIMER);
        final ListPreference beeppref = group.findPreference(CameraSettings.KEY_TIMER_SOUND_EFFECTS);
        item = makeItem(R.drawable.ic_timer);
        item.setLabel(res.getString(R.string.pref_camera_timer_title).toUpperCase(locale));
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                CountdownTimerPopup timerPopup = (CountdownTimerPopup) mActivity.getLayoutInflater().inflate(
                        R.layout.countdown_setting_popup, null, false);
                timerPopup.initialize(ctpref, beeppref);
                timerPopup.setSettingChangedListener(PhotoMenu.this);
                mUI.dismissPopup();
                mPopup = timerPopup;
                mPopupStatus = POPUP_SECOND_LEVEL;
                mUI.showPopup(mPopup);
            }
        });
        more.addItem(item);
        // Image size.
        item = makeItem(R.drawable.ic_imagesize);
        final ListPreference sizePref = group.findPreference(CameraSettings.KEY_PICTURE_SIZE);
        item.setLabel(res.getString(R.string.pref_camera_picturesize_title).toUpperCase(locale));
        item.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(PieItem item) {
                ListPrefSettingPopup popup = (ListPrefSettingPopup) mActivity.getLayoutInflater().inflate(
                        R.layout.list_pref_setting_popup, null, false);
                popup.initialize(sizePref);
                popup.setSettingChangedListener(PhotoMenu.this);
                mUI.dismissPopup();
                mPopup = popup;
                mPopupStatus = POPUP_SECOND_LEVEL;
                mUI.showPopup(mPopup);
            }
        });
        more.addItem(item);

        // extra settings popup
        mOtherKeys = new String[] {
                CameraSettings.KEY_STORAGE,
                CameraSettings.KEY_SLOW_SHUTTER,
        };

        if (!isEmptyMenu(mPreferenceGroup, mOtherKeys)){
            item = makeItem(R.drawable.ic_settings_holo_light);
            item.setLabel(res.getString(R.string.camera_menu_more_label).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    if (mPopup == null || mPopupStatus != POPUP_FIRST_LEVEL) {
                        LayoutInflater inflater = mActivity.getLayoutInflater();
                        MoreSettingPopup popup = (MoreSettingPopup) inflater.inflate(
                                R.layout.more_setting_popup, null, false);
                        popup.initialize(mPreferenceGroup, mOtherKeys);
                        popup.setSettingChangedListener(PhotoMenu.this);
                        mPopup = popup;
                        mPopupStatus = POPUP_FIRST_LEVEL;
                    }
                    mUI.showPopup(mPopup);
                }
            });
            more.addItem(item);
        }

        // White balance.
        if (group.findPreference(CameraSettings.KEY_WHITE_BALANCE) != null) {
            item = makeItem(CameraSettings.KEY_WHITE_BALANCE);
            item.setLabel(res.getString(R.string.pref_camera_whitebalance_label));
            more.addItem(item);
        }
        // scene mode
        final ListPreference scenePref = group.findPreference(CameraSettings.KEY_SCENE_MODE);
        if (scenePref != null) {
            item = makeItem(R.drawable.ic_sce);
            item.setLabel(res.getString(R.string.pref_camera_scenemode_title).toUpperCase(locale));
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(PieItem item) {
                    LayoutInflater inflater = mActivity.getLayoutInflater();
                    ListPrefSettingPopup popup = (ListPrefSettingPopup) inflater.inflate(
                            R.layout.list_pref_setting_popup, null, false);
                    popup.initialize(scenePref);
                    popup.setSettingChangedListener(PhotoMenu.this);
                    mUI.dismissPopup();
                    mPopup = popup;
                    mPopupStatus = POPUP_SECOND_LEVEL;
                    mUI.showPopup(mPopup);
                }
            });
            more.addItem(item);
        }
        // burst mode
        if (group.findPreference(CameraSettings.KEY_BURST_MODE) != null) {
            IconListPreference burstPref = (IconListPreference) group.findPreference(
                    CameraSettings.KEY_BURST_MODE);
            mUI.updateBurstModeIcon(Integer.valueOf(burstPref.getValue()));
            item = makeItem(CameraSettings.KEY_BURST_MODE);
            item.setLabel(res.getString(R.string.pref_camera_burst_title));
            more.addItem(item);
        }
    }

    @Override
    // Hit when an item in the second-level popup gets selected
    public void onListPrefChanged(ListPreference pref) {
        if (mPopup != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mUI.dismissPopup();
            }
        }
        onSettingChanged(pref);
    }

    public void popupDismissed() {
        // if the 2nd level popup gets dismissed
        if (mPopup != null) {
            if (mPopupStatus == POPUP_SECOND_LEVEL) {
                mPopup = null;
            }
        }
    }

    // Return true if the preference has the specified key but not the value.
    private static boolean notSame(ListPreference pref, String key, String value) {
        return (key.equals(pref.getKey()) && !value.equals(pref.getValue()));
    }

    private void setPreference(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null && !value.equals(pref.getValue())) {
            pref.setValue(value);
            reloadPreferences();
        }
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        // Reset the scene mode if HDR is set to on. Reset HDR if scene mode is
        // set to non-auto.
        if (notSame(pref, CameraSettings.KEY_CAMERA_HDR, mSettingOff)) {
            setPreference(CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO);
        } else if (notSame(pref, CameraSettings.KEY_SCENE_MODE, Parameters.SCENE_MODE_AUTO)) {
            setPreference(CameraSettings.KEY_CAMERA_HDR, mSettingOff);
        }
        super.onSettingChanged(pref);
    }

    @Override
    // Hit when an item in the first-level popup gets selected, then bring up
    // the second-level popup
    public void onPreferenceClicked(ListPreference pref) {
        if (mPopupStatus != POPUP_FIRST_LEVEL) return;

        LayoutInflater inflater = mActivity.getLayoutInflater();
        ListPrefSettingPopup basic = (ListPrefSettingPopup) inflater.inflate(
                R.layout.list_pref_setting_popup, null, false);
        basic.initialize(pref);
        basic.setSettingChangedListener(this);
        mUI.dismissPopup();
        mPopup = basic;
        mUI.showPopup(mPopup);
        mPopupStatus = POPUP_SECOND_LEVEL;
    }

    public boolean isEmptyMenu(PreferenceGroup group, String[] keys) {
        for (int i = 0; i < keys.length; ++i) {
            ListPreference pref = group.findPreference(keys[i]);
            if (pref != null) {
                return false;
            }
        }
        return true;
    }
}

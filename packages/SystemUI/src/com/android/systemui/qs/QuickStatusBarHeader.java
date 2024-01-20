/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.internal.graphics.ColorUtils;
import com.android.systemui.Dependency;
import android.graphics.drawable.Drawable;
import android.widget.*;

import android.os.Handler;
import android.content.Intent;
import android.provider.Settings;
import com.android.systemui.R;
import com.android.systemui.util.LargeScreenUtils;

import com.android.settingslib.Utils;

import android.bluetooth.BluetoothAdapter;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;
import com.android.settingslib.bluetooth.LocalBluetoothProfileManager;
import com.android.systemui.qs.tiles.dialog.BluetoothDialogFactory;
import com.android.systemui.qs.tiles.dialog.InternetDialogFactory;
import com.android.systemui.statusbar.connectivity.AccessPointController;

import com.android.systemui.plugins.ActivityStarter;

import com.android.systemui.tuner.TunerService;

/**
 * View that contains the top-most bits of the QS panel (primarily the status bar with date, time,
 * battery, carrier info and privacy icons) and also contains the {@link QuickQSPanel}.
 */
public class QuickStatusBarHeader extends FrameLayout implements TunerService.Tunable, View.OnClickListener, View.OnLongClickListener, 
        BluetoothCallback {

    private boolean mExpanded;
    private boolean mQsDisabled;

    private boolean mBluetoothEnable = false;
    private boolean mInternetEnable = true;

    private View mQQsLayout;

    private LinearLayout mBTTile;
    private LinearLayout mInetTile;
    private ImageView mBluetoothIcon;
    private TextView mBluetoothText;
    private ImageView mBtChevron;
    private ImageView mInternetIcon;
    private TextView mInternetText;
    private ImageView mInternetChevron;
    private BluetoothDialogFactory mBluetoothDialogFactory;
    private InternetDialogFactory mInternetDialogFactory;
    private AccessPointController mAccessPointController;
    private BluetoothAdapter mBluetoothAdapter;
    private LocalBluetoothManager mLocalBluetoothManager;

    private int colorIconActive, colorNonActive, colorIconNonActive;

    private final ActivityStarter mActivityStarter;

    private static final String QS_HEADER_IMAGE =
            "system:" + Settings.System.QS_HEADER_IMAGE;

    protected QuickQSPanel mHeaderQsPanel;

    // QS Header
    private ImageView mQsHeaderImageView;
    private View mQsHeaderLayout;
    private boolean mHeaderImageEnabled;
    private int mHeaderImageValue;

    public QuickStatusBarHeader(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mHeaderQsPanel = findViewById(R.id.quick_qs_panel);

        mQsHeaderLayout = findViewById(R.id.layout_header);
        mQsHeaderImageView = findViewById(R.id.qs_header_image_view);
        mQsHeaderImageView.setClipToOutline(true);

        mQQsLayout = findViewById(R.id.afl_qs_container);

        colorIconActive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.colorPrimary);
        colorIconNonActive = Utils.getColorAttrDefaultColor(mContext, android.R.attr.textColorPrimary);
        colorNonActive =
            Utils.getColorAttrDefaultColor(mContext, com.android.internal.R.attr.textColorPrimaryInverse);

        mBTTile = findViewById(R.id.afl_bluetooth);
        mBTTile.setOnClickListener(this);
        mBTTile.setOnLongClickListener(this);
        mBluetoothIcon = findViewById(R.id.afl_icon_bt);
        mBluetoothText = findViewById(R.id.afl_text_bt);
        mBtChevron = findViewById(R.id.bt_chevron);

        mInetTile = findViewById(R.id.afl_inet);
        mInetTile.setOnClickListener(this);
        mInetTile.setOnLongClickListener(this);
        mInternetIcon = findViewById(R.id.afl_qs_internet_icon);
        mInternetText = findViewById(R.id.afl_qs_internet_text);
        mInternetChevron = findViewById(R.id.inet_chevron);

        LocalBluetoothManager localBluetoothManager = mLocalBluetoothManager =
                LocalBluetoothManager.getInstance(mContext, /* onInitCallback= */ null);
        if (localBluetoothManager != null) {
            localBluetoothManager.getEventManager().registerCallback(this);
            updateBluetoothState(
                    localBluetoothManager.getBluetoothAdapter().getBluetoothState());
        }
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        updateResources();

        Dependency.get(TunerService.class).addTunable(this, QS_HEADER_IMAGE);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case QS_HEADER_IMAGE:
                mHeaderImageValue =
                       TunerService.parseInteger(newValue, 0);
                mHeaderImageEnabled = mHeaderImageValue != 0;
                updateResources();
                break;
            default:
                break;
        }
    }

    private void updateQSHeaderImage() {
        if (!mHeaderImageEnabled) {
            mQsHeaderLayout.setVisibility(View.GONE);
            return;
        }
        Configuration config = mContext.getResources().getConfiguration();
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            boolean mIsNightMode = (mContext.getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int fadeFilter = ColorUtils.blendARGB(Color.TRANSPARENT, mIsNightMode ?
                Color.BLACK : Color.WHITE, 30 / 100f);
            int resId = getResources().getIdentifier("qs_header_image_" +
                String.valueOf(mHeaderImageValue), "drawable", "com.android.systemui");
            mQsHeaderImageView.setImageResource(resId);
            mQsHeaderImageView.setColorFilter(fadeFilter, PorterDuff.Mode.SRC_ATOP);
            mQsHeaderLayout.setVisibility(View.VISIBLE);
        } else {
            mQsHeaderLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateResources();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Only react to touches inside QuickQSPanel
        if (event.getY() > mQQsLayout.getTop()) {
            return super.onTouchEvent(event);
        } else {
            return false;
        }
    }

    void updateResources() {
        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (mQsDisabled) {
            lp.height = 0;
        } else {
            lp.height = WRAP_CONTENT;
        }
        setLayoutParams(lp);

        MarginLayoutParams qqsAFL = (MarginLayoutParams) mQQsLayout.getLayoutParams();
        if (largeScreenHeaderActive) {
            qqsAFL.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qqs_layout_margin_top);
        } else {
            qqsAFL.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height);
        }
        mQQsLayout.setLayoutParams(qqsAFL);

        MarginLayoutParams qqsLP = (MarginLayoutParams) mHeaderQsPanel.getLayoutParams();
        if (largeScreenHeaderActive) {
            qqsLP.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qqs_layout_margin_top_2);
        } else {
            qqsLP.topMargin = mContext.getResources()
                    .getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height) * 3;
        }
        mHeaderQsPanel.setLayoutParams(qqsLP);
        updateQSHeaderImage();
    }

    public void setExpanded(boolean expanded, QuickQSPanelController quickQSPanelController) {
        if (mExpanded == expanded) return;
        mExpanded = expanded;
        updateEverything();

        Resources resources = mContext.getResources();
        boolean largeScreenHeaderActive =
                LargeScreenUtils.shouldUseLargeScreenShadeHeader(resources);


        MarginLayoutParams qqsAFL = (MarginLayoutParams) mQQsLayout.getLayoutParams();
        if (largeScreenHeaderActive) {
            qqsAFL.topMargin = expanded ? mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qqs_layout_margin_top) *2 : mContext.getResources()
                    .getDimensionPixelSize(R.dimen.qqs_layout_margin_top);
        } else {
            qqsAFL.topMargin = expanded ? mContext.getResources()
                    .getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height) *3 : mContext.getResources()
                    .getDimensionPixelSize(R.dimen.large_screen_shade_header_min_height) ;
        }
        mQQsLayout.setLayoutParams(qqsAFL);

        quickQSPanelController.setExpanded(expanded);
    }

    public void disable(int state1, int state2, boolean animate) {
        final boolean disabled = (state2 & DISABLE2_QUICK_SETTINGS) != 0;
        if (disabled == mQsDisabled) return;
        mQsDisabled = disabled;
        mHeaderQsPanel.setDisabledByPolicy(disabled);
        updateResources();
    }

    public void updateEverything() {
        post(() -> setClickable(!mExpanded));
    }

    private void setContentMargins(View view, int marginStart, int marginEnd) {
        MarginLayoutParams lp = (MarginLayoutParams) view.getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        view.setLayoutParams(lp);
    }

    public void setBluetoothDialogFactory(BluetoothDialogFactory bluetoothDialogFactory) {
        mBluetoothDialogFactory = bluetoothDialogFactory;
    }

    void updateBluetoothState(int state) {
        switch (state) {
            case BluetoothAdapter.STATE_TURNING_ON:
                updateBluetoothState(true);
                break;
            case BluetoothAdapter.STATE_ON:
                updateBluetoothState(true);
                break;
            case BluetoothAdapter.STATE_TURNING_OFF:
                updateBluetoothState(false);
                break;
            case BluetoothAdapter.STATE_OFF:
                updateBluetoothState(false);
                break;
        }
    }

    public void updateBluetoothState(boolean state) {
        mBluetoothEnable = state;
        updateBluetoothTile();
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        updateBluetoothState(bluetoothState);
    }

    public void updateBluetoothTile() {
        boolean state = mBluetoothEnable;
        Drawable bg = mBTTile.getBackground();
        bg.setTint(state ? colorIconNonActive : colorIconActive);
        mBluetoothIcon.setColorFilter(state ? colorIconNonActive : colorIconActive);
        mBluetoothText.setTextColor(state ? colorIconActive : colorNonActive);
        mBtChevron.setColorFilter(state ? colorIconNonActive : colorIconActive);
    }

    public void updateInternetTile() {
        boolean state = mInternetEnable;
        Drawable bg = mInetTile.getBackground();
        bg.setTint(state ? colorIconActive : colorIconNonActive);
        mInternetIcon.setColorFilter(state ? colorIconActive : colorIconNonActive);
        mInternetText.setTextColor(state ? colorIconActive : colorNonActive);
        mInternetChevron.setColorFilter(state ? colorIconActive : colorIconNonActive);
    }

    public ImageView getInternetIcon() {
        return mInternetIcon;
    }

    public void setInternetDialogFactory(InternetDialogFactory internetDialogFactory) {
        mInternetDialogFactory = internetDialogFactory;
    }

    public void setAccessPointController(AccessPointController accessPointController) {
        mAccessPointController = accessPointController;
    }

    @Override
    public void onClick(View v) {
    	 if (v == mBTTile) {
          new Handler().post(() ->
                mBluetoothDialogFactory.create(true, v));
    	 } else if ( v == mInetTile) {
    	 	new Handler().post(() ->
                mInternetDialogFactory.create(true,
                mAccessPointController.canConfigMobileData(),
                mAccessPointController.canConfigWifi(), v));
    	 }
    }

    @Override
    public boolean onLongClick(View v) {
    	if (v == mBTTile) {
    		mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                Settings.ACTION_BLUETOOTH_SETTINGS), 0);
          return true;
    	} else if ( v == mInetTile ) {
    		mActivityStarter.postStartActivityDismissingKeyguard(new Intent(
                Settings.ACTION_WIFI_SETTINGS), 0);
          return true;
    	}
    	return false;
    }
}

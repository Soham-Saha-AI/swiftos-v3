package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Layout;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

/** Small static weather pill; source details are available in the launcher menu. */
public final class WeatherWidget extends LinearLayout {
    private final TextView city;
    private final TextView temperature;
    private final TextView condition;
    private final WeatherIconDrawable icon = new WeatherIconDrawable();
    private final LinearLayout pill;
    private TextView dateAnchor;
    private String dateText;
    private final TextWatcher dateChanges = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
            String next = text == null ? "" : text.toString();
            if (!TextUtils.equals(dateText, next)) {
                dateText = next;
                requestLayout();
            }
        }
        @Override public void afterTextChanged(Editable text) {}
    };

    public WeatherWidget(Context context) { this(context, null); }

    public WeatherWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setFocusable(false);
        setClipChildren(false);
        setClipToPadding(false);

        pill = new LinearLayout(context);
        pill.setOrientation(HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(9), 0, dp(9), 0);
        pill.setBackground(new GlassMaterialDrawable(context, 18));
        addView(pill, new LayoutParams(LayoutParams.MATCH_PARENT, dp(28)));

        ImageView image = new ImageView(context);
        image.setImageDrawable(icon);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams imageParams = new LayoutParams(dp(19), dp(19));
        imageParams.rightMargin = dp(6);
        pill.addView(image, imageParams);

        city = label(11f, R.font.outfit_medium, 0xF2FFFFFF);
        city.setText(R.string.weather_city);
        pill.addView(city, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        temperature = label(11f, R.font.outfit_medium, Color.WHITE);
        LayoutParams temperatureParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        temperatureParams.leftMargin = dp(8);
        pill.addView(temperature, temperatureParams);

        condition = label(11f, R.font.outfit_medium, 0xCFFFFFFF);
        LayoutParams conditionParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        conditionParams.leftMargin = dp(8);
        pill.addView(condition, conditionParams);
        setWeather(null);
    }

    /** The date's rendered line width is a size guide, while the pill still hugs its own content. */
    public void setDateAnchor(TextView date) {
        if (dateAnchor == date) return;
        if (dateAnchor != null) dateAnchor.removeTextChangedListener(dateChanges);
        dateAnchor = date;
        dateText = date == null ? null : date.getText().toString();
        if (date != null) date.addTextChangedListener(dateChanges);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(widthMeasureSpec);
        if (mode == MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int maximum = mode == MeasureSpec.UNSPECIFIED ? dp(500) : MeasureSpec.getSize(widthMeasureSpec);
        Layout dateLayout = dateAnchor == null ? null : dateAnchor.getLayout();
        float dateWidth = 0f;
        if (dateLayout != null) {
            for (int line = 0; line < dateLayout.getLineCount(); line++) {
                dateWidth = Math.max(dateWidth, dateLayout.getLineWidth(line));
            }
        }
        // Labels keep one fixed size; only a very long condition may ellipsize at this width limit.
        if (dateWidth > 0f) maximum = Math.min(maximum, Math.round(dateWidth * 1.10f));
        else maximum = Math.min(maximum, dp(212));
        super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(0, maximum), MeasureSpec.AT_MOST), heightMeasureSpec);
    }

    public void setWeather(WeatherReading reading) {
        if (reading == null || !reading.isUsable(System.currentTimeMillis())) {
            city.setText(R.string.weather_city);
            temperature.setText("—°C");
            condition.setText(R.string.weather_unavailable);
            icon.setWeather("cloud", true);
            pill.setAlpha(0.6f);
            pill.setContentDescription(getContext().getString(R.string.weather_unavailable_description));
            return;
        }
        boolean fresh = reading.isFresh(System.currentTimeMillis());
        city.setText(reading.city);
        temperature.setText(reading.formattedTemperature());
        condition.setText(fresh ? reading.condition() : getContext().getString(R.string.weather_cached));
        icon.setWeather(reading.iconKind(), reading.isDay);
        pill.setAlpha(fresh ? 1f : 0.7f);
        String updated = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(reading.observedAtMs));
        pill.setContentDescription(getContext().getString(R.string.weather_description,
                reading.city, reading.formattedTemperature(), reading.condition(), updated));
    }

    private TextView label(float sizeSp, int font, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(getResources().getFont(font));
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setFocusable(false);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

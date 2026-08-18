package com.zcz.javatavern;

import android.view.View;
import android.widget.AdapterView;

import java.util.function.IntConsumer;

final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
    private final IntConsumer onSelected;

    SimpleItemSelectedListener(IntConsumer onSelected) {
        this.onSelected = onSelected;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        onSelected.accept(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }
}

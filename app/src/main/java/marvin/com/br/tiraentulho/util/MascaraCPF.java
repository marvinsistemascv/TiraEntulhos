package marvin.com.br.tiraentulho.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class MascaraCPF implements TextWatcher {

    private boolean isUpdating;
    private final EditText editText;

    public MascaraCPF(EditText editText) {
        this.editText = editText;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (isUpdating) {
            isUpdating = false;
            return;
        }

        String str = s.toString().replaceAll("[^\\d]", "");

        StringBuilder formatted = new StringBuilder();

        int i = 0;
        for (char c : str.toCharArray()) {
            if (i == 3 || i == 6) formatted.append(".");
            if (i == 9) formatted.append("-");
            formatted.append(c);
            i++;
        }

        isUpdating = true;
        editText.setText(formatted.toString());
        editText.setSelection(formatted.length());
    }

    @Override
    public void afterTextChanged(Editable s) {}
}
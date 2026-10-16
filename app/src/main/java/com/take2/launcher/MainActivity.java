package com.take2.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class MainActivity extends Activity {

    private static final String FORM_URL = "https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=RW3KIsgYkU2JfKa8RPBVe0_sWrmhg51GttEa2bZlFupUMTMxTlZRWk5GOU41RVI4TzJMTERGRjJYNi4u&origin=QRCode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Uri uri = Uri.parse(FORM_URL);

        Intent samsungInternet = new Intent(Intent.ACTION_VIEW, uri);
        samsungInternet.setPackage("com.sec.android.app.sbrowser");

        try {
            startActivity(samsungInternet);
        } catch (ActivityNotFoundException e) {
            Intent fallbackBrowser = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(fallbackBrowser);
        } finally {
            finish();
        }
    }
}

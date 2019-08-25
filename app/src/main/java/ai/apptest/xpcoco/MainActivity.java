package ai.apptest.xpcoco;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;
import android.content.SharedPreferences;

public class MainActivity extends AppCompatActivity {
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView textView = findViewById(R.id.PackageName);
        mPrefs = getSharedPreferences("Configuration", MODE_PRIVATE);

        Intent intent = new Intent(this.getIntent());
        String s = intent.getStringExtra("InjectionTarget");
        if(s != null ){
            SharedPreferences.Editor e = mPrefs.edit();
            e.putString("InjectionTarget", s);
            e.commit();
        }else{
            s = mPrefs.getString("InjectionTarget", null);
            if( s == null)
                s = "No Injection Target ";
        }

        textView.setText(s);
    }
}

package fr.guillaumevillena.opendnsupdater.activity;

import android.graphics.Color;
import android.os.Bundle;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import fr.guillaumevillena.opendnsupdater.OpenDnsUpdater;
import fr.guillaumevillena.opendnsupdater.R;
import fr.guillaumevillena.opendnsupdater.activity.introSlide.IntroAccountFragment;
import fr.guillaumevillena.opendnsupdater.activity.introSlide.IntroHowItWorks;
import fr.guillaumevillena.opendnsupdater.activity.introSlide.IntroMainActionAcccount;
import fr.guillaumevillena.opendnsupdater.utils.PreferenceCodes;

public class ApplicationWizard extends AppIntro {

    private Boolean hasAccount = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //Welcom slide
        SliderPage welcomeSlide = new SliderPage();
        welcomeSlide.setTitle(getString(R.string.app_intro_welcom_title));
        welcomeSlide.setDescription("Welcome to OpenDNS Updater\nThe following steps will help you to configure the application");
        welcomeSlide.setImageDrawable(R.drawable.cellphone_settings_variant);
        welcomeSlide.setBgColor(getResources().getColor(R.color.colorPrimary));
        addSlide(AppIntroFragment.newInstance(welcomeSlide));

        addSlide(IntroHowItWorks.newInstance());

        addSlide(IntroAccountFragment.newInstance());

        addSlide(IntroMainActionAcccount.newInstance());


        // OPTIONAL METHODS
        // Override bar/separator color.
        setBarColor(getResources().getColor(R.color.colorPrimary));
        setSeparatorColor(Color.parseColor("#2196F3"));

        // Hide Skip/Done button.
        showSkipButton(false);
        setProgressButtonEnabled(true);
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        // Do something when users tap on Skip button.
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {

        super.onDonePressed(currentFragment);
        if (currentFragment instanceof IntroMainActionAcccount) {
            if (((IntroMainActionAcccount) currentFragment).isConnectionOk()) {
                OpenDnsUpdater.getPrefs().edit().putBoolean(PreferenceCodes.FIRST_TIME_CONFIG_FINISHED, true).apply();
                finish();
            }
        }
        // Do something when users tap on Done button.
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {

        if (oldFragment instanceof IntroAccountFragment && newFragment instanceof IntroMainActionAcccount) {
            IntroAccountFragment hasAccountFragment = (IntroAccountFragment) oldFragment;
            IntroMainActionAcccount accountActionFragment = (IntroMainActionAcccount) newFragment;
            hasAccount = hasAccountFragment.hasAccount();
            accountActionFragment.visible(hasAccount);
        }

        super.onSlideChanged(oldFragment, newFragment);
        // Do something when the slide changes.


    }
}

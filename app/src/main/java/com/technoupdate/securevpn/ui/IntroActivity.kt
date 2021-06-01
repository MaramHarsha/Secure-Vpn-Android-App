package com.technoupdate.securevpn.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.Nullable
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro
import com.github.paolorotolo.appintro.AppIntroFragment
import com.github.paolorotolo.appintro.model.SliderPage
import com.technoupdate.securevpn.GlobalApp
import com.technoupdate.securevpn.R


class IntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Slide 1
        val sliderPage1 = SliderPage()
        sliderPage1.title = "Best Secure VPN Servers"
        sliderPage1.description = "Choose from any VPN Servers and stay connected anonymous until you prefer"
        sliderPage1.imageDrawable = R.drawable.ic_shield
        sliderPage1.bgColor = Color.parseColor("#141415")
        sliderPage1.titleColor = Color.parseColor("#ffffff")
        sliderPage1.descColor = Color.parseColor("#ffffff")
        addSlide(AppIntroFragment.newInstance(sliderPage1))

        val sliderPage2 = SliderPage()
        sliderPage2.title = "Unlimited Access to Unlimited Servers"
        sliderPage2.description = "Choose from a range of servers free without an additional cost and safeguard your privacy"
        sliderPage2.imageDrawable = R.drawable.ic_key
        sliderPage2.bgColor = Color.parseColor("#141415")
        sliderPage2.titleColor = Color.parseColor("#ffffff")
        sliderPage2.descColor = Color.parseColor("#ffffff")
        addSlide(AppIntroFragment.newInstance(sliderPage2))

        val sliderPage3 = SliderPage()
        sliderPage3.title = "Unblock Websites!"
        sliderPage3.description = "Unlock websites which are blocked in your country and enjoy the premium benefit of VPN"
        sliderPage3.imageDrawable = R.drawable.ic_http
        sliderPage3.bgColor = Color.parseColor("#141415")
        sliderPage3.titleColor = Color.parseColor("#ffffff")
        sliderPage3.descColor = Color.parseColor("#ffffff")
        addSlide(AppIntroFragment.newInstance(sliderPage3))
    }

    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Do something when users tap on Skip button.
        startActivity(Intent(applicationContext, MainActivity::class.java))
        GlobalApp.getInstance().dataUtil.isIntroShown = true
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Do something when users tap on Done button.
        startActivity(Intent(applicationContext, MainActivity::class.java))
        GlobalApp.getInstance().dataUtil.isIntroShown = true
        finish()
    }

    override fun onSlideChanged(@Nullable oldFragment: Fragment?, @Nullable newFragment: Fragment?) {
        super.onSlideChanged(oldFragment, newFragment)
        // Do something when the slide changes.
    }
}

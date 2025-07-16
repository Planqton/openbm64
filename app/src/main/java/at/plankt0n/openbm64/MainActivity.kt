package at.plankt0n.openbm64

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, HomeFragment())
                        .commit()
                    true
                }
                R.id.navigation_setup -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, SetupFragment())
                        .commit()
                    true
                }
                R.id.navigation_third -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ThirdFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }
    }
}

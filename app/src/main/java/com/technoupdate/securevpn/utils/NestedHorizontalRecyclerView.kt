package com.technoupdate.securevpn.utils

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class NestedHorizontalRecyclerView : RecyclerView {
    private val Y_BUFFER = 10
    private var preX = 0f
    private var preY = 0f

    constructor(context: Context) : this(context, null){
        addOnItemTouchListener()
    }
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0){
        addOnItemTouchListener()
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr){
        addOnItemTouchListener()
    }

    private fun addOnItemTouchListener(){
        this.addOnItemTouchListener(object: OnItemTouchListener {
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {

            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {

            }

            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> rv.parent.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.x - preX) > abs(e.y - preY)) {
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        } else if (abs(e.y - preY) > Y_BUFFER) {
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        }

                    }
                }
                preX = e.x
                preY = e.y
                return false
            }
        })
    }
}
package com.premnirmal.Magnet;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import com.facebook.rebound.Spring;
import com.facebook.rebound.SpringConfig;
import com.facebook.rebound.SpringListener;
import com.facebook.rebound.SpringSystem;
import com.tumblr.backboard.Actor;
import com.tumblr.backboard.MotionProperty;
import com.tumblr.backboard.imitator.Imitator;
import com.tumblr.backboard.imitator.InertialImitator;
import com.tumblr.backboard.performer.Performer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by prem on 7/20/14.
 * Desc: Class holding the Magnet Icon, and performing touchEvents on the view.
 */
public class Magnet implements SpringListener, View.OnTouchListener, View.OnClickListener {

  protected View iconView;
  protected RemoveView removeView;
  protected WindowManager windowManager;
  protected WindowManager.LayoutParams layoutParams;
  protected Context context;
  protected boolean shouldStickToWall = true;
  protected boolean shouldFlingAway = true;
  protected IconCallback iconCallback;
  protected int[] iconPosition = new int[2];
  protected long lastTouchDown;
  protected boolean isBeingDragged = false;
  protected int iconWidth = -1, iconHeight = -1;
  protected int initialX = -1, initialY = -1;
  protected Spring xSpring, ySpring;
  protected Actor actor;
  protected MagnetImitator motionImitatorX;
  protected MagnetImitator motionImitatorY;
  protected int xMinValue;
  protected int xMaxValue;
  protected int yMinValue;
  protected int yMaxValue;
  protected int screenWidth;
  protected int screenHeight;

  public static Builder<Magnet> newBuilder(Context context) {
    return new MagnetBuilder(context);
  }

  private static class MagnetBuilder extends Builder<Magnet> {

    MagnetBuilder(Context context) {
      super(Magnet.class, context);
    }

    @Override public Magnet build() {
      return super.build();
    }
  }

  /**
   * Builder class to create your {@link Magnet}
   */
  public static class Builder<T extends Magnet> {

    protected T magnet;

    /**
     * Used to instantiate your subclass of {@link Magnet}
     *
     * @param clazz your subclass
     */
    public Builder(Class<T> clazz, Context context) {
      final Constructor<T> constructor;
      try {
        constructor = clazz.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        magnet = constructor.newInstance(context);
      } catch (NoSuchMethodException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (IllegalAccessException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (InstantiationException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      } catch (InvocationTargetException e) {
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    }

    /**
     * The Icon must have a view, provide a view or a layout using {@link #setIconView(int)}
     *
     * @param iconView the view representing the icon
     */
    public Builder<T> setIconView(View iconView) {
      magnet.iconView = iconView;
      return this;
    }

    /**
     * Use an xml layout to provide the button view
     *
     * @param iconViewRes the layout id of the icon
     */
    public Builder<T> setIconView(int iconViewRes) {
      return setIconView(LayoutInflater.from(magnet.context).inflate(iconViewRes, null));
    }

    /**
     * whether your magnet sticks to the edge of your screen when you release it
     */
    public Builder<T> setShouldStickToWall(boolean shouldStick) {
      magnet.shouldStickToWall = shouldStick;
      return this;
    }

    /**
     * whether you can fling away your Magnet towards the bottom of the screen
     */
    public Builder<T> setShouldFlingAway(boolean shoudlFling) {
      magnet.shouldFlingAway = shoudlFling;
      return this;
    }

    /**
     * Callback for when the icon moves, or when it isis flung away and destroyed
     */
    public Builder<T> setIconCallback(IconCallback callback) {
      magnet.iconCallback = callback;
      return this;
    }

    /**
     *
     * @param shouldBeResponsive
     * @return
     */
    public Builder<T> setRemoveIconShouldBeResponsive(boolean shouldBeResponsive) {
      magnet.removeView.shouldBeResponsive = shouldBeResponsive;
      return this;
    }

    /**
     * you can set a custom remove icon or use the default one
     */
    public Builder<T> setRemoveIconResId(int removeIconResId) {
      magnet.removeView.setIconResId(removeIconResId);
      return this;
    }

    /**
     * you can set a custom remove icon shadow or use the default one
     */
    public Builder<T> setRemoveIconShadow(int shadow) {
      magnet.removeView.setShadowBG(shadow);
      return this;
    }

    /**
     * Set the initial coordinates of the magnet
     */
    public Builder<T> setInitialPosition(int x, int y) {
      magnet.initialX = x;
      magnet.initialY = y;
      return this;
    }

    /**
     * Set a custom width for the icon view. default is {@link WindowManager.LayoutParams#WRAP_CONTENT}
     */
    public Builder<T> setIconWidth(int width) {
      magnet.iconWidth = width;
      return this;
    }

    /**
     * * Set a custom height for the icon view. default is {@link WindowManager.LayoutParams#WRAP_CONTENT}
     */
    public Builder<T> setIconHeight(int height) {
      magnet.iconHeight = height;
      return this;
    }

    public T build() {
      if (magnet.iconView == null) {
        throw new NullPointerException("Magnet view is null! Must set a view for the magnet!");
      }
      return magnet;
    }
  }

  public Magnet(Context context) {
    this.context = context;
    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    removeView = new RemoveView(context);
  }

  @NonNull protected SpringConfig getSpringConfig() {
    SpringConfig config = SpringConfig.defaultConfig;//SpringConfig.fromBouncinessAndSpeed(0.5, 10);
    return config;
  }

  protected int getStatusBarHeight() {
    int result = 0;
    int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
    if (resourceId > 0) {
      result = context.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  protected int getNavBarHeight() {
    int result = 0;
    int resourceId =
        context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
    if (resourceId > 0) {
      return context.getResources().getDimensionPixelSize(resourceId);
    }
    return result;
  }

  protected void addToWindow() {
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        iconWidth > 0 ? iconWidth : WindowManager.LayoutParams.WRAP_CONTENT,
        iconHeight > 0 ? iconHeight : WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSPARENT);
    params.gravity = Gravity.TOP | Gravity.START;
    windowManager.addView(iconView, layoutParams = params);
  }

  protected float pxFromDp(float dp) {
    return dp * context.getResources().getDisplayMetrics().density;
  }

  protected int getMinVelocity() {
    float minVelocoty = pxFromDp(3);
    return (int) minVelocoty;
  }

  protected void flingAway() {
    if (shouldFlingAway) {
      //int y = mContext.getResources().getDisplayMetrics().heightPixels / 2;
      //int x = 0;
      //mAnimator.start(x, y);
      //if (mListener != null) {
      //  mListener.onFlingAway();
      //}
      //destroy();
    }
  }

  protected void showRemoveView() {
    if (removeView != null && shouldFlingAway) {
      removeView.show();
    }
  }

  protected void hideRemoveView() {
    if (removeView != null && shouldFlingAway) {
      removeView.hide();
    }
  }

  protected void goToWall() {
    if (shouldStickToWall) {
      iconView.getLocationOnScreen(iconPosition);
      int maxX = screenWidth;
      int maxY = screenHeight;
      boolean endX = iconPosition[0] > maxX / 2;
      boolean endY = iconPosition[1] > maxY / 2;
      float nearestXWall = endX ? maxX : 0;
      float nearestYWall = endY ? maxY : 0;
      if (Math.abs(iconPosition[0] - nearestXWall) < Math.abs(iconPosition[1] - nearestYWall)) {
        if (!endX) {
          xSpring.setEndValue(xMinValue);
        } else {
          xSpring.setEndValue(xMaxValue);
        }
      } else {
        if (!endY) {
          ySpring.setEndValue(yMinValue);
        } else {
          ySpring.setEndValue(yMaxValue);
        }
      }
    }
  }

  /**
   * Show the Magnet i.e. add it to the Window
   */
  public void show() {
    addToWindow();
    iconView.setOnClickListener(this);
    SpringConfig config = getSpringConfig();
    SpringSystem springSystem = SpringSystem.create();
    xSpring = springSystem.createSpring();
    xSpring.setSpringConfig(config);
    ySpring = springSystem.createSpring();
    ySpring.setSpringConfig(config);

    motionImitatorX =
        new MagnetImitator(MotionProperty.X, Imitator.TRACK_ABSOLUTE, Imitator.FOLLOW_SPRING, 0, 0);

    motionImitatorY =
        new MagnetImitator(MotionProperty.Y, Imitator.TRACK_ABSOLUTE, Imitator.FOLLOW_SPRING, 0, 0);

    actor = new Actor.Builder(springSystem, iconView).addMotion(xSpring, motionImitatorX,
        new WindowManagerPerformer(MotionProperty.X))
        .addMotion(ySpring, motionImitatorY, new WindowManagerPerformer(MotionProperty.Y))
        .onTouchListener(this)
        .build();
    screenWidth = context.getResources().getDisplayMetrics().widthPixels;
    screenHeight = context.getResources().getDisplayMetrics().heightPixels;
    iconView.getViewTreeObserver()
        .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override public void onGlobalLayout() {
            xMinValue = -iconView.getMeasuredWidth() / 2;
            motionImitatorX.setMinValue(xMinValue);
            xMaxValue = screenWidth - iconView.getMeasuredWidth() / 2;
            motionImitatorX.setMaxValue(xMaxValue);
            yMinValue = getStatusBarHeight() - iconView.getMeasuredHeight() / 2;
            motionImitatorY.setMinValue(yMinValue);
            yMaxValue = screenHeight - getNavBarHeight() - iconView.getMeasuredHeight() / 2;
            motionImitatorY.setMaxValue(yMaxValue);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
              iconView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            } else {
              iconView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
          }
        });

    if (initialX != -1 || initialY != -1) {
      setPosition(initialX, initialY);
    } else {
      goToWall();
    }
    xSpring.addListener(this);
    ySpring.addListener(this);
  }

  /**
   * Move the icon to the given position.
   *
   * @param x The x coordinate to move to.
   * @param y The y coordinate to move to.
   */
  public void setPosition(int x, int y) {
    actor.removeAllListeners();
    xSpring.setEndValue(x);
    ySpring.setEndValue(y);
    actor.addAllListeners();
  }

  /**
   * Destroys the magnet - removes the view from the WindowManager and calls
   * {@link IconCallback#onIconDestroyed()}
   */
  public void destroy() {
    actor.removeAllListeners();
    xSpring.setAtRest();
    ySpring.setAtRest();
    windowManager.removeView(iconView);
    if (removeView != null) {
      removeView.destroy();
    }
    if (iconCallback != null) {
      iconCallback.onIconDestroyed();
    }
    context = null;
  }

  // View.OnTouchListener

  @Override public boolean onTouch(View view, MotionEvent event) {
    int action = event.getAction();
    if (action == MotionEvent.ACTION_DOWN) {
      isBeingDragged = true;
      lastTouchDown = System.currentTimeMillis();
      showRemoveView();
      return true;
    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      isBeingDragged = false;
      hideRemoveView();
      return true;
    }
    return false;
  }

  // View.OnClickListener

  @Override public void onClick(View view) {
    xSpring.setAtRest();
    ySpring.setAtRest();
    view.getLocationOnScreen(iconPosition);
    if (iconCallback != null) {
      iconCallback.onIconClick(view, iconPosition[0], iconPosition[1]);
    }
  }

  // SpringListener

  @Override public void onSpringUpdate(Spring spring) {
    iconView.getLocationOnScreen(iconPosition);

    if (iconCallback != null) {
      iconCallback.onMove(iconPosition[0], iconPosition[1]);
    }
  }

  @Override public void onSpringAtRest(Spring spring) {

  }

  @Override public void onSpringActivate(Spring spring) {

  }

  @Override public void onSpringEndStateChange(Spring spring) {

  }

  class MagnetImitator extends InertialImitator {

    MagnetImitator(@NonNull MotionProperty property, int trackStrategy, int followStrategy,
        double minValue, double maxValue) {
      super(property, trackStrategy, followStrategy, minValue, maxValue);
    }

    @Override public void constrain(MotionEvent event) {
      super.constrain(event);
      Log.d("Backboard", "constrain");
    }

    @Override public void release(MotionEvent event) {
      super.release(event);
      Log.d("Backboard", "release");
      goToWall();
    }

    @Override public void imitate(final View view, @NonNull final MotionEvent event) {
      final float viewValue;
      if (mProperty == MotionProperty.X) {
        viewValue = layoutParams.x;
      } else if (mProperty == MotionProperty.Y) {
        viewValue = layoutParams.y;
      } else {
        viewValue = 0;
      }
      final float eventValue = mProperty.getValue(event);
      mOffset = mProperty.getOffset(view);
      if (event.getHistorySize() > 0) {
        final float historicalValue = mProperty.getOldestValue(event);
        imitate(viewValue + mOffset, eventValue, eventValue - historicalValue, event);
      } else {
        imitate(viewValue + mOffset, eventValue, 0, event);
      }
    }
  }

  class WindowManagerPerformer extends Performer {

    final MotionProperty motionProperty;

    WindowManagerPerformer(MotionProperty motionProperty) {
      super(null, null);
      this.motionProperty = motionProperty;
    }

    @Override public void onSpringUpdate(@NonNull Spring spring) {
      double currentValue = spring.getCurrentValue();
      if (motionProperty == MotionProperty.X) {
        layoutParams.x = (int) currentValue;
        windowManager.updateViewLayout(iconView, layoutParams);
      } else if (motionProperty == MotionProperty.Y) {
        layoutParams.y = (int) currentValue;
        windowManager.updateViewLayout(iconView, layoutParams);
      }
    }

    @Override public void onSpringAtRest(Spring spring) {
      super.onSpringAtRest(spring);
      Log.d("Backboard", "onSpringAtRest");
      goToWall();
    }
  }
}

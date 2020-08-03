/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.layout;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AndroidRuntimeException;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.GlobalWindowInsetsManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutCallback;
import com.github.adamantcheese.chan.ui.captcha.AuthenticationLayoutInterface;
import com.github.adamantcheese.chan.ui.captcha.CaptchaHolder;
import com.github.adamantcheese.chan.ui.captcha.CaptchaLayout;
import com.github.adamantcheese.chan.ui.captcha.GenericWebViewAuthenticationLayout;
import com.github.adamantcheese.chan.ui.captcha.LegacyCaptchaLayout;
import com.github.adamantcheese.chan.ui.captcha.v1.CaptchaNojsLayoutV1;
import com.github.adamantcheese.chan.ui.captcha.v2.CaptchaNoJsLayoutV2;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.helper.ImagePickDelegate;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.theme.DropdownArrowDrawable;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.SelectionListeningEditText;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.ImageDecoder;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.model.data.descriptor.ChanDescriptor;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.requestViewAndKeyboardFocus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

// TODO(KurobaEx): When catalog reply is opened and we open any thread via "tabs" the opened thread
//  will be glitched, it won't have the bottomNavBar because we have a replyLayout opened.
public class ReplyLayout
        extends LoadView
        implements View.OnClickListener,
        ReplyPresenter.ReplyPresenterCallback,
        TextWatcher,
        ImageDecoder.ImageDecoderCallback,
        SelectionListeningEditText.SelectionChangedListener,
        CaptchaHolder.CaptchaValidationListener {
    private static final String TAG = "ReplyLayout";

    @Inject
    ReplyPresenter presenter;
    @Inject
    CaptchaHolder captchaHolder;
    @Inject
    ThemeHelper themeHelper;
    @Inject
    SiteRepository siteRepository;
    @Inject
    BoardRepository boardRepository;
    @Inject
    GlobalWindowInsetsManager globalWindowInsetsManager;

    private ReplyLayoutCallback callback;
    private AuthenticationLayoutInterface authenticationLayout;

    private boolean blockSelectionChange = false;

    // Progress view (when sending request to the server)
    private View progressLayout;
    private TextView currentProgress;

    // Reply views:
    private View replyInputLayout;
    private TextView message;
    private EditText name;
    private EditText subject;
    private EditText flag;
    private EditText options;
    private EditText fileName;
    private LinearLayout nameOptions;
    private Button commentQuoteButton;
    private Button commentSpoilerButton;
    private Button commentCodeButton;
    private Button commentEqnButton;
    private Button commentMathButton;
    private Button commentSJISButton;
    private SelectionListeningEditText comment;
    private TextView commentCounter;
    private CheckBox spoiler;
    private LinearLayout previewHolder;
    private ImageView preview;
    private TextView previewMessage;
    private ImageView attach;
    private ConstraintLayout captcha;
    private TextView validCaptchasCount;
    private ImageView more;
    private ImageView submit;
    private DropdownArrowDrawable moreDropdown;
    @Nullable
    private HintPopup hintPopup = null;

    // Captcha views:
    private FrameLayout captchaContainer;
    private ImageView captchaHardReset;
    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private Runnable closeMessageRunnable = new Runnable() {
        @Override
        public void run() {
            message.setVisibility(GONE);
        }
    };

    public ReplyLayout(Context context) {
        this(context, null);
    }

    public ReplyLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ReplyLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        EventBus.getDefault().register(this);

        Disposable disposable = globalWindowInsetsManager.listenForKeyboardChanges()
                .subscribe((isOpened) -> setWrappingMode(presenter.isExpanded()));

        compositeDisposable.add(disposable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }

        EventBus.getDefault().unregister(this);
        compositeDisposable.clear();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        inject(this);

        // Inflate reply input
        replyInputLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_input, this, false);
        message = replyInputLayout.findViewById(R.id.message);
        name = replyInputLayout.findViewById(R.id.name);
        subject = replyInputLayout.findViewById(R.id.subject);
        flag = replyInputLayout.findViewById(R.id.flag);
        options = replyInputLayout.findViewById(R.id.options);
        fileName = replyInputLayout.findViewById(R.id.file_name);
        nameOptions = replyInputLayout.findViewById(R.id.name_options);
        commentQuoteButton = replyInputLayout.findViewById(R.id.comment_quote);
        commentSpoilerButton = replyInputLayout.findViewById(R.id.comment_spoiler);
        commentCodeButton = replyInputLayout.findViewById(R.id.comment_code);
        commentEqnButton = replyInputLayout.findViewById(R.id.comment_eqn);
        commentMathButton = replyInputLayout.findViewById(R.id.comment_math);
        commentSJISButton = replyInputLayout.findViewById(R.id.comment_sjis);
        comment = replyInputLayout.findViewById(R.id.comment);
        commentCounter = replyInputLayout.findViewById(R.id.comment_counter);
        spoiler = replyInputLayout.findViewById(R.id.spoiler);
        preview = replyInputLayout.findViewById(R.id.preview);
        previewHolder = replyInputLayout.findViewById(R.id.preview_holder);
        previewMessage = replyInputLayout.findViewById(R.id.preview_message);
        attach = replyInputLayout.findViewById(R.id.attach);
        captcha = replyInputLayout.findViewById(R.id.captcha_container);
        validCaptchasCount = replyInputLayout.findViewById(R.id.valid_captchas_count);
        more = replyInputLayout.findViewById(R.id.more);
        submit = replyInputLayout.findViewById(R.id.submit);

        progressLayout = AndroidUtils.inflate(getContext(), R.layout.layout_reply_progress, this, false);
        currentProgress = progressLayout.findViewById(R.id.current_progress);

        spoiler.setButtonTintList(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));
        spoiler.setTextColor(ColorStateList.valueOf(themeHelper.getTheme().textPrimary));

        // Setup reply layout views
        fileName.setOnLongClickListener(v -> presenter.fileNameLongClicked());
        commentQuoteButton.setOnClickListener(this);
        commentSpoilerButton.setOnClickListener(this);
        commentCodeButton.setOnClickListener(this);
        commentMathButton.setOnClickListener(this);
        commentEqnButton.setOnClickListener(this);
        commentSJISButton.setOnClickListener(this);

        comment.addTextChangedListener(this);
        comment.setSelectionChangedListener(this);
        comment.setOnFocusChangeListener((view, focused) -> {
            if (!focused) hideKeyboard(comment);
        });
        comment.setPlainTextPaste(true);
        setupCommentContextMenu();

        previewHolder.setOnClickListener(this);

        moreDropdown = new DropdownArrowDrawable(
                dp(16),
                dp(16),
                false,
                getAttrColor(getContext(), R.attr.dropdown_dark_color),
                getAttrColor(getContext(), R.attr.dropdown_dark_pressed_color)
        );
        more.setImageDrawable(moreDropdown);
        AndroidUtils.setBoundlessRoundRippleBackground(more);
        more.setOnClickListener(this);

        themeHelper.getTheme().imageDrawable.apply(attach);
        AndroidUtils.setBoundlessRoundRippleBackground(attach);
        attach.setOnClickListener(this);
        attach.setOnLongClickListener(v -> {
            presenter.onAttachClicked(true);
            return true;
        });
        attach.setClickable(true);
        attach.setFocusable(true);

        ImageView captchaImage = replyInputLayout.findViewById(R.id.captcha);
        AndroidUtils.setBoundlessRoundRippleBackground(captchaImage);
        captcha.setOnClickListener(this);

        themeHelper.getTheme().sendDrawable.apply(submit);
        AndroidUtils.setBoundlessRoundRippleBackground(submit);
        submit.setOnClickListener(this);
        submit.setOnLongClickListener(v -> {
            presenter.onSubmitClicked(true);
            return true;
        });

        // Inflate captcha layout
        captchaContainer = (FrameLayout) AndroidUtils.inflate(
                getContext(),
                R.layout.layout_reply_captcha,
                this,
                false
        );

        captchaHardReset = captchaContainer.findViewById(R.id.reset);

        // Setup captcha layout views
        captchaContainer.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        themeHelper.getTheme().refreshDrawable.apply(captchaHardReset);
        AndroidUtils.setBoundlessRoundRippleBackground(captchaHardReset);
        captchaHardReset.setOnClickListener(this);

        setView(replyInputLayout);

        // Presenter
        presenter.create(this);
    }

    public void setCallback(ReplyLayoutCallback callback) {
        this.callback = callback;
    }

    public ReplyPresenter getPresenter() {
        return presenter;
    }

    public void onOpen(boolean open) {
        presenter.onOpen(open);
    }

    public void bindLoadable(ChanDescriptor chanDescriptor) {
        Site site = siteRepository.bySiteDescriptor(chanDescriptor.siteDescriptor());
        if (site == null) {
            throw new IllegalStateException("Couldn't find site by siteDescriptor " + chanDescriptor.siteDescriptor());
        }

        if (site.actions().postRequiresAuthentication()) {
            comment.setMinHeight(dp(144));
        } else {
            captcha.setVisibility(GONE);
        }

        presenter.bindChanDescriptor(chanDescriptor);
        captchaHolder.setListener(this);
    }

    public void cleanup() {
        captchaHolder.removeListener();
        presenter.unbindChanDescriptor();
        removeCallbacks(closeMessageRunnable);
    }

    public boolean onBack() {
        return presenter.onBack();
    }

    private void setWrappingMode(boolean matchParent) {
        LayoutParams params = (LayoutParams) getLayoutParams();
        params.width = MATCH_PARENT;
        params.height = matchParent ? MATCH_PARENT : WRAP_CONTENT;

        int bottomPadding = 0;
        if (!globalWindowInsetsManager.isKeyboardOpened()) {
            bottomPadding = globalWindowInsetsManager.bottom();
        }

        if (matchParent) {
            setPadding(0, ((ThreadListLayout) getParent()).toolbarHeight(), 0, bottomPadding);
            params.gravity = Gravity.TOP;
        } else {
            setPadding(0, 0, 0, bottomPadding);
            params.gravity = Gravity.BOTTOM;
        }

        setLayoutParams(params);
    }

    @Override
    public void onClick(View v) {
        if (v == more) {
            presenter.onMoreClicked();
        } else if (v == attach) {
            presenter.onAttachClicked(false);
        } else if (v == captcha) {
            presenter.onAuthenticateCalled();
        } else if (v == submit) {
            presenter.onSubmitClicked(false);
        } else if (v == previewHolder) {
            // prevent immediately removing the file
            attach.setClickable(false);
            callback.showImageReencodingWindow(presenter.isAttachedFileSupportedForReencoding());
        } else if (v == captchaHardReset) {
            if (authenticationLayout != null) {
                authenticationLayout.hardReset();
            }
        } else if (v == commentQuoteButton) {
            insertQuote();
        } else if (v == commentSpoilerButton) {
            insertTags("[spoiler]", "[/spoiler]");
        } else if (v == commentCodeButton) {
            insertTags("[code]", "[/code]");
        } else if (v == commentEqnButton) {
            insertTags("[eqn]", "[/eqn]");
        } else if (v == commentMathButton) {
            insertTags("[math]", "[/math]");
        } else if (v == commentSJISButton) {
            insertTags("[sjis]", "[/sjis]");
        }
    }

    @SuppressWarnings("ConstantConditions")
    private boolean insertQuote() {
        int selectionStart = comment.getSelectionStart();
        int selectionEnd = comment.getSelectionEnd();

        String[] textLines = comment.getText()
                .subSequence(selectionStart, selectionEnd)
                .toString()
                .split("\n");

        StringBuilder rebuilder = new StringBuilder();

        for (int i = 0; i < textLines.length; i++) {
            rebuilder.append(">").append(textLines[i]);

            if (i != textLines.length - 1) {
                rebuilder.append("\n");
            }
        }

        comment.getText().replace(selectionStart, selectionEnd, rebuilder.toString());
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    private boolean insertTags(String before, String after) {
        int selectionStart = comment.getSelectionStart();

        comment.getText().insert(comment.getSelectionEnd(), after);
        comment.getText().insert(selectionStart, before);
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public void initializeAuthentication(
            Site site,
            SiteAuthentication authentication,
            AuthenticationLayoutCallback callback,
            boolean useV2NoJsCaptcha,
            boolean autoReply
    ) {
        if (authenticationLayout == null) {
            authenticationLayout = createAuthenticationLayout(authentication, useV2NoJsCaptcha);
            captchaContainer.addView((View) authenticationLayout, 0);
        }

        if (!(authenticationLayout instanceof LegacyCaptchaLayout)) {
            hideKeyboard(this);
        }

        authenticationLayout.initialize(site, callback, autoReply);
        authenticationLayout.reset();
    }

    private AuthenticationLayoutInterface createAuthenticationLayout(
            SiteAuthentication authentication,
            boolean useV2NoJsCaptcha
    ) {
        switch (authentication.type) {
            case CAPTCHA1:
                return (LegacyCaptchaLayout) AndroidUtils.inflate(getContext(),
                        R.layout.layout_captcha_legacy,
                        captchaContainer,
                        false
                );
            case CAPTCHA2:
                return new CaptchaLayout(getContext());
            case CAPTCHA2_NOJS:
                AuthenticationLayoutInterface authenticationLayoutInterface;

                if (useV2NoJsCaptcha) {
                    // new captcha window without webview
                    authenticationLayoutInterface = new CaptchaNoJsLayoutV2(getContext());
                } else {
                    // default webview-based captcha view
                    authenticationLayoutInterface = new CaptchaNojsLayoutV1(getContext());
                }

                ImageView resetButton = captchaContainer.findViewById(R.id.reset);
                if (resetButton != null) {
                    if (useV2NoJsCaptcha) {
                        // we don't need the default reset button because we have our own
                        resetButton.setVisibility(GONE);
                    } else {
                        // restore the button's visibility when using old v1 captcha view
                        resetButton.setVisibility(VISIBLE);
                    }
                }

                return authenticationLayoutInterface;
            case GENERIC_WEBVIEW:
                GenericWebViewAuthenticationLayout view =
                        new GenericWebViewAuthenticationLayout(getContext());

                LayoutParams params = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
                view.setLayoutParams(params);

                return view;
            case NONE:
            default:
                throw new IllegalArgumentException();
        }
    }

    @Override
    public void setPage(ReplyPresenter.Page page) {
        Logger.d(TAG, "Switching to page " + page.name());
        switch (page) {
            case LOADING:
                setWrappingMode(false);
                setView(progressLayout);

                //reset progress to 0 upon uploading start
                currentProgress.setVisibility(INVISIBLE);
                break;
            case INPUT:
                setView(replyInputLayout);
                setWrappingMode(presenter.isExpanded());
                break;
            case AUTHENTICATION:
                setWrappingMode(true);
                setView(captchaContainer);
                captchaContainer.requestFocus(View.FOCUS_DOWN);
                break;
        }

        if (page != ReplyPresenter.Page.AUTHENTICATION) {
            destroyCurrentAuthentication();
        }
    }

    @Override
    public void resetAuthentication() {
        authenticationLayout.reset();
    }

    @Override
    public void destroyCurrentAuthentication() {
        if (authenticationLayout == null) {
            return;
        }

        // cleanup resources when switching from the new to the old captcha view
        if (authenticationLayout instanceof CaptchaNoJsLayoutV2) {
            ((CaptchaNoJsLayoutV2) authenticationLayout).onDestroy();
        }

        captchaContainer.removeView((View) authenticationLayout);
        authenticationLayout = null;
    }

    @Override
    public void showAuthenticationFailedError(Throwable error) {
        String message = getString(R.string.could_not_initialized_captcha, getReason(error));
        showToast(getContext(), message, Toast.LENGTH_LONG);
    }

    private String getReason(Throwable error) {
        if (error instanceof AndroidRuntimeException && error.getMessage() != null) {
            if (error.getMessage().contains("MissingWebViewPackageException")) {
                return getString(R.string.fail_reason_webview_is_not_installed);
            }

            // Fallthrough
        } else if (error instanceof Resources.NotFoundException) {
            return getString(R.string.fail_reason_some_part_of_webview_not_initialized, error.getMessage());
        }

        if (error.getMessage() != null) {
            return String.format("%s: %s", error.getClass().getSimpleName(), error.getMessage());
        }

        return error.getClass().getSimpleName();
    }

    @Override
    public void loadDraftIntoViews(Reply draft) {
        name.setText(draft.name);
        subject.setText(draft.subject);
        flag.setText(draft.flag);
        options.setText(draft.options);
        blockSelectionChange = true;
        comment.setText(draft.comment);
        blockSelectionChange = false;
        fileName.setText(draft.fileName);
        spoiler.setChecked(draft.spoilerImage);
    }

    @Override
    public void loadViewsIntoDraft(Reply draft) {
        draft.name = name.getText().toString();
        draft.subject = subject.getText().toString();
        draft.flag = flag.getText().toString();
        draft.options = options.getText().toString();
        draft.comment = comment.getText().toString();
        draft.fileName = fileName.getText().toString();
        draft.spoilerImage = spoiler.isChecked();
    }

    @Override
    public int getSelectionStart() {
        return comment.getSelectionStart();
    }

    @Override
    public void adjustSelection(int start, int amount) {
        try {
            comment.setSelection(start + amount);
        } catch (Exception e) {
            // set selection to the end if it fails for any reason
            comment.setSelection(comment.getText().length());
        }
    }

    @Override
    public void openMessage(String text) {
        if (text == null) {
            text = "";
        }

        removeCallbacks(closeMessageRunnable);
        message.setText(text);
        message.setVisibility(TextUtils.isEmpty(text) ? GONE : VISIBLE);

        if (!TextUtils.isEmpty(text)) {
            postDelayed(closeMessageRunnable, 5000);
        }
    }

    @Override
    public void onPosted() {
        showToast(getContext(), R.string.reply_success);
        callback.openReply(false);
        callback.requestNewPostLoad();
    }

    @Override
    public void setCommentHint(String hint) {
        comment.setHint(hint);
    }

    @Override
    public void showCommentCounter(boolean show) {
        commentCounter.setVisibility(show ? VISIBLE : GONE);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        setWrappingMode(presenter.isExpanded());
    }

    @Override
    public void setExpanded(boolean expanded) {
        setWrappingMode(expanded);

        comment.setMaxLines(expanded ? 500 : 6);
        previewHolder.setLayoutParams(
                new LinearLayout.LayoutParams(MATCH_PARENT, expanded ? dp(150) : dp(100))
        );

        float startRotation = 1f;
        float endRotation = 0f;

        ValueAnimator animator = ValueAnimator.ofFloat(
                expanded ? startRotation : endRotation,
                expanded ? endRotation : startRotation
        );

        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.setDuration(400);
        animator.addUpdateListener(animation -> {
            moreDropdown.setRotation((float) animation.getAnimatedValue());
        });

        animator.start();
    }

    @Override
    public void openNameOptions(boolean open) {
        nameOptions.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openSubject(boolean open) {
        subject.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openFlag(boolean open) {
        flag.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void openCommentQuoteButton(boolean open) {
        commentQuoteButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSpoilerButton(boolean open) {
        commentSpoilerButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentCodeButton(boolean open) {
        commentCodeButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentEqnButton(boolean open) {
        commentEqnButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentMathButton(boolean open) {
        commentMathButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openCommentSJISButton(boolean open) {
        commentSJISButton.setVisibility(open ? View.VISIBLE : View.GONE);
    }

    @Override
    public void openFileName(boolean open) {
        fileName.setVisibility(open ? VISIBLE : GONE);
    }

    @Override
    public void setFileName(String name) {
        fileName.setText(name);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void updateCommentCount(int count, int maxCount, boolean over) {
        commentCounter.setText(count + "/" + maxCount);

        int textColor = over
                ? 0xffff0000
                : getAttrColor(getContext(), R.attr.text_color_secondary);

        commentCounter.setTextColor(textColor);
    }

    public void focusComment() {
        //this is a hack to make sure text is selectable
        comment.setEnabled(false);
        comment.setEnabled(true);
        comment.post(() -> requestViewAndKeyboardFocus(comment));
    }

    @Override
    public void onFallbackToV1CaptchaView(boolean autoReply) {
        // fallback to v1 captcha window
        presenter.switchPage(ReplyPresenter.Page.AUTHENTICATION, false, autoReply);
    }

    @Override
    public void openPreview(boolean show, File previewFile) {
        previewHolder.setClickable(false);

        if (show) {
            ImageDecoder.decodeFileOnBackgroundThread(previewFile, dp(400), dp(300), this);
            themeHelper.getTheme().clearDrawable.apply(attach);
        } else {
            spoiler.setVisibility(GONE);
            previewHolder.setVisibility(GONE);
            previewMessage.setVisibility(GONE);
            callback.updatePadding();
            themeHelper.getTheme().imageDrawable.apply(attach);
        }

        // the delay is taken from LayoutTransition, as this class is set to automatically animate
        // layout changes only allow the preview to be clicked if it is fully visible
        postDelayed(() -> previewHolder.setClickable(true), 300);
    }

    @Override
    public void openPreviewMessage(boolean show, String message) {
        previewMessage.setVisibility(show ? VISIBLE : GONE);
        previewMessage.setText(message);
    }

    @Override
    public void openSpoiler(boolean show, boolean setUnchecked) {
        spoiler.setVisibility(show ? VISIBLE : GONE);

        if (setUnchecked) {
            spoiler.setChecked(false);
        }
    }

    @Override
    public void onImageBitmap(Bitmap bitmap) {
        if (bitmap != null) {
            preview.setImageBitmap(bitmap);
            previewHolder.setVisibility(VISIBLE);
            callback.updatePadding();

            showReencodeImageHint();
        } else {
            openPreviewMessage(true, getString(R.string.reply_no_preview));
        }
    }

    @Override
    public void highlightPostNo(int no) {
        callback.highlightPostNo(no);
    }

    @Override
    public void onSelectionChanged() {
        if (!blockSelectionChange) {
            presenter.onSelectionChanged();
        }
    }

    private void setupCommentContextMenu() {
        comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            private MenuItem quoteMenuItem;
            private MenuItem spoilerMenuItem;
            private MenuItem codeMenuItem;
            private MenuItem mathMenuItem;
            private MenuItem eqnMenuItem;
            private MenuItem sjisMenuItem;
            private boolean processed;

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                ChanThread thread = callback.getThread();
                if (thread == null) {
                    return true;
                }

                ChanDescriptor chanDescriptor = thread.getChanDescriptor();
                Board board = boardRepository.getFromBoardDescriptor(chanDescriptor.boardDescriptor());
                if (board == null) {
                    return true;
                }

                boolean is4chan = chanDescriptor.siteDescriptor().is4chan();
                String boardCode = chanDescriptor.boardCode();

                // menu item cleanup, these aren't needed for this
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (menu.size() > 0) {
                        menu.removeItem(android.R.id.shareText);
                    }
                }

                // setup standard items
                // >greentext
                quoteMenuItem = menu.add(Menu.NONE, R.id.reply_selection_action_quote, 1, R.string.post_quote);

                // [spoiler] tags
                if (board.spoilers) {
                    spoilerMenuItem = menu.add(Menu.NONE,
                            R.id.reply_selection_action_spoiler,
                            2,
                            R.string.reply_comment_button_spoiler
                    );
                }

                // setup specific items in a submenu
                SubMenu otherMods = menu.addSubMenu("Modify");
                // g [code]
                if (is4chan && boardCode.equals("g")) {
                    codeMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_code,
                            1,
                            R.string.reply_comment_button_code
                    );
                }

                // sci [eqn] and [math]
                if (is4chan && boardCode.equals("sci")) {
                    eqnMenuItem = otherMods.add(Menu.NONE,
                            R.id.reply_selection_action_eqn,
                            2,
                            R.string.reply_comment_button_eqn
                    );

                    mathMenuItem = otherMods.add(
                            Menu.NONE,
                            R.id.reply_selection_action_math,
                            3,
                            R.string.reply_comment_button_math
                    );
                }

                // jp and vip [sjis]
                if (is4chan && (boardCode.equals("jp") || boardCode.equals("vip"))) {
                    sjisMenuItem = otherMods.add(
                            Menu.NONE,
                            R.id.reply_selection_action_sjis,
                            4,
                            R.string.reply_comment_button_sjis
                    );
                }

                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item == quoteMenuItem) {
                    processed = insertQuote();
                } else if (item == spoilerMenuItem) {
                    processed = insertTags("[spoiler]", "[/spoiler]");
                } else if (item == codeMenuItem) {
                    processed = insertTags("[code]", "[/code]");
                } else if (item == eqnMenuItem) {
                    processed = insertTags("[eqn]", "[/eqn]");
                } else if (item == mathMenuItem) {
                    processed = insertTags("[math]", "[/math]");
                } else if (item == sjisMenuItem) {
                    processed = insertTags("[sjis]", "[/sjis]");
                }

                if (processed) {
                    mode.finish();
                    processed = false;
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }
        });
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        presenter.onCommentTextChanged(comment.getText());
    }

    @Override
    public void showThread(@NotNull ChanDescriptor.ThreadDescriptor threadDescriptor) {
        callback.showThread(threadDescriptor);
    }

    @Override
    public ImagePickDelegate getImagePickDelegate() {
        return ((StartActivity) getContext()).getImagePickDelegate();
    }

    @Override
    public ChanThread getThread() {
        return callback.getThread();
    }

    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        if (filenameRemoved) {
            // update edit field with new filename
            fileName.setText(reply.fileName);
        } else {
            // update reply with existing filename (may have been changed by user)
            reply.fileName = fileName.getText().toString();
        }

        presenter.onImageOptionsApplied(reply);
    }

    public void onImageOptionsComplete() {
        // reencode windows gone, allow the file to be removed
        attach.setClickable(true);
    }

    private void showReencodeImageHint() {
        if (ChanSettings.reencodeHintShown.get()) {
            return;
        }

        String message = getString(R.string.click_image_for_extra_options);
        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }

        hintPopup = HintPopup.show(getContext(), preview, message, dp(-32), dp(16));
        hintPopup.wiggle();

        ChanSettings.reencodeHintShown.set(true);
    }

    @Override
    public void onUploadingProgress(int percent) {
        if (currentProgress != null) {
            if (percent >= 0) {
                currentProgress.setVisibility(VISIBLE);
            }

            currentProgress.setText(String.valueOf(percent));
        }
    }

    @Override
    public void onCaptchaCountChanged(int validCaptchaCount) {
        if (validCaptchaCount == 0) {
            validCaptchasCount.setVisibility(INVISIBLE);
        } else {
            validCaptchasCount.setVisibility(VISIBLE);
        }

        validCaptchasCount.setText(String.valueOf(validCaptchaCount));
    }

    public interface ReplyLayoutCallback {
        void highlightPostNo(int no);

        void openReply(boolean open);

        void showThread(ChanDescriptor.ThreadDescriptor threadDescriptor);

        void requestNewPostLoad();

        @Nullable
        ChanThread getThread();

        void showImageReencodingWindow(boolean supportsReencode);

        void updatePadding();
    }
}

package av.imageview;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.view.View;
import android.widget.ImageView;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GifRequestBuilder;
import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.target.SimpleTarget;

import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiRHelper.ResourceNotFoundException;
import org.appcelerator.titanium.view.TiDrawableReference;
import org.appcelerator.titanium.view.TiUIView;

public class ExtendedImageView extends TiUIView {
	private static final String LCAT = "ExtendedImageView";

    private TiViewProxy proxy;

    private String source;
    private ImageView imageView;
    private ProgressBar progressBar;
    private RelativeLayout layout;

    //Config variables
    private boolean loadingIndicator;
    private boolean memoryCache;
    private String defaultImage;
    private String brokenImage;
    private String contentMode;

    private RequestListener<String, GlideDrawable> requestListener;

    public ExtendedImageView(TiViewProxy proxy) {
        super(proxy);

        this.proxy = proxy;

        //Setting up default values
        this.loadingIndicator = true;
        this.contentMode = ImageviewAndroidModule.CONTENT_MODE_ASPECT_FIT;
        this.memoryCache = true;

        //Setting up layout and imageview
        layout = new RelativeLayout(this.proxy.getActivity());
        imageView = new ImageView(this.proxy.getActivity());

        layout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        imageView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));

        try {
            progressBar = new ProgressBar(this.proxy.getActivity(), null, TiRHelper.getAndroidResource("attr.progressBarStyleSmall"));

            progressBar.setProgressDrawable(ContextCompat.getDrawable(this.proxy.getActivity().getBaseContext(), TiRHelper.getApplicationResource("drawable.circular_progress")));
            progressBar.setVisibility(View.INVISIBLE);

            RelativeLayout.LayoutParams progressBarStyle = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            progressBarStyle.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);

            progressBar.setLayoutParams(progressBarStyle);

            layout.addView(imageView);
            layout.addView(progressBar);
        } catch (ResourceNotFoundException exception) {
            layout.addView(imageView);
        }

        setNativeView(layout);
    }

    @Override
    public void processProperties(KrollDict d) {
        super.processProperties(d);

        if (d.containsKey("loadingIndicator"))
            this.setLoadingIndicator(d.getBoolean("loadingIndicator"));
        if (d.containsKey("enableMemoryCache"))
            this.setMemoryCache(d.getBoolean("enableMemoryCache"));
        if (d.containsKey("contentMode"))
            this.setContentMode(d.getString("contentMode"));
        if (d.containsKey("defaultImage"))
            this.setDefaultImage(d.getString("defaultImage"));
        if (d.containsKey("brokenLinkImage"))
            this.setBrokenLinkImage(d.getString("brokenLinkImage"));
        if (d.containsKey("image"))
            this.setSource(d.getString("image"));
    }

    public void setSource(String url) {
        this.source = sanitizeUrl(url);

        //If is correctly set I'll display the image
        if (this.source != null)
            this.setImage(this.source);
    }

    public void setImage(String image) {
        if (TiApplication.isUIThread())
            startRequest(image, this.loadingIndicator);
        else
            this.proxy.getActivity().runOnUiThread(new ImageLoader(this, this.source));
    }

    public void setBlob(TiBlob blob) {
        if (TiApplication.isUIThread())
            this.displayBlob(blob);
        else
            this.proxy.getActivity().runOnUiThread(new ImageLoader(this, blob));
    }

    public void displayBlob(TiBlob blob) {
        TiDrawableReference drawableReference = TiDrawableReference.fromBlob(this.proxy.getActivity(), blob);

		this.imageView.setScaleType((this.contentMode != null && this.contentMode.equals(ImageviewAndroidModule.CONTENT_MODE_ASPECT_FILL)) ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        this.imageView.setImageBitmap(drawableReference.getBitmap());

        drawableReference = null;
        blob = null;
    }

    public void startRequest(String url, Boolean loadingIndicator) {
		RequestListenerBuilder requestListenerBuilder = new RequestListenerBuilder();

		GifRequestBuilder gifRequestBuilder;
		DrawableRequestBuilder drawableRequestBuilder;

        Drawable defaultImageDrawable = (this.defaultImage != null) ? TiDrawableReference.fromUrl(proxy, this.defaultImage).getDrawable() : null;
        Drawable brokenLinkImageDrawable = (this.brokenImage != null) ? TiDrawableReference.fromUrl(proxy, this.brokenImage).getDrawable() : null;

		if (this.loadingIndicator)
        	this.progressBar.setVisibility(View.VISIBLE);

		//Handling GIF
		if (this.getMimeType(url) != null && this.getMimeType(url) == "image/gif") {
			gifRequestBuilder = Glide.with(this.proxy.getActivity().getBaseContext()).load(url).asGif()
				.skipMemoryCache(this.memoryCache)
				.diskCacheStrategy(DiskCacheStrategy.SOURCE)
				.placeholder(defaultImageDrawable)
				.error(brokenLinkImageDrawable)
				.listener(requestListenerBuilder.getListenerForUrl(url));

			if (this.contentMode == null || this.contentMode.equals(ImageviewAndroidModule.CONTENT_MODE_ASPECT_FIT))
	            gifRequestBuilder.fitCenter().into(this.imageView);
	        else
	            gifRequestBuilder.centerCrop().into(this.imageView);
		}
		//Handling simple images
		else {
			drawableRequestBuilder = Glide.with(this.proxy.getActivity().getBaseContext()).load(url)
				.skipMemoryCache(this.memoryCache)
				.placeholder(defaultImageDrawable)
				.error(brokenLinkImageDrawable)
				.listener(requestListenerBuilder.getListenerForUrl(url));

			if (this.contentMode == null || this.contentMode.equals(ImageviewAndroidModule.CONTENT_MODE_ASPECT_FIT))
	            drawableRequestBuilder.fitCenter().into(this.imageView);
        	else
	            drawableRequestBuilder.centerCrop().into(this.imageView);
		}
    }

    private String sanitizeUrl(String url) {
        if (url == null || !(url instanceof String))
            return null;

        return (url.toString().startsWith("http") || url.toString().startsWith("ftp")) ? url.toString() : this.proxy.resolveUrl(null, url.toString());
    }

	private String getMimeType(String url) {
        String type = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);

        if (extension != null)
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

        return type;
    }

	private void handleException(Exception e) {
		if (progressBar.getVisibility() == View.VISIBLE)
			progressBar.setVisibility(View.INVISIBLE);

		if (proxy.hasListeners("error")) {
			KrollDict payload = new KrollDict();

			payload.put("image", source);
			payload.put("reason", e.getMessage());

			proxy.fireEvent("error", payload);
		}
	}

	private void handleResourceReady() {
		if (progressBar.getVisibility() == View.VISIBLE)
			progressBar.setVisibility(View.INVISIBLE);

		if (proxy.hasListeners("load")) {
			KrollDict payload = new KrollDict();

			payload.put("image", source);

			proxy.fireEvent("load", payload);
		}
	}

    @Override
	public void release() {
		super.release();
	}

    //Config stuff
    synchronized public String getImage() {
        return this.source;
    }

    synchronized public void setLoadingIndicator(boolean enabled) {
        this.loadingIndicator = enabled;
    }

    synchronized public void setMemoryCache(boolean enabled) {
        this.memoryCache = enabled;
    }

    synchronized public void setContentMode(String contentMode) {
        this.contentMode = contentMode;
    }

    synchronized public void setBrokenLinkImage(String url) {
        this.brokenImage = url;
    }

    synchronized public void setDefaultImage(String url) {
        this.defaultImage = url;
    }

    synchronized public boolean getLoadingIndicator() {
        return this.loadingIndicator;
    }

    synchronized public boolean getMemoryCache() {
        return this.memoryCache;
    }

    synchronized public String getContentMode() {
        return this.contentMode;
    }

    synchronized public String getBrokenLinkImage() {
        return this.brokenImage;
    }

    synchronized public String getDefaultImage() {
        return this.defaultImage;
    }

	//Utility to create a specific request listener
	private class RequestListenerBuilder {
        private String LCAT = "RequestListenerBuilder";

        public RequestListener getListenerForUrl(String url) {
            if (url ==  null)
                return null;

            if (getMimeType(url) == "image/gif") {
                return new RequestListener<String, GifDrawable>() {
                    @Override
                    public boolean onException(Exception e, String model, Target<GifDrawable> target, boolean isFirstResource) {
                        handleException(e);

                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GifDrawable resource, String model, Target<GifDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                        handleResourceReady();

                        return false;
                    }
                };
            }

            return new RequestListener<String, GlideDrawable>() {
                @Override
                public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
					handleException(e);

                    return false;
                }

                @Override
                public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
					handleResourceReady();

                    return false;
                }
            };
        }
    }
}

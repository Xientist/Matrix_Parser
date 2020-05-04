package com.example.matrixparser;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import boofcv.abst.distort.FDistort;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.camera2.VisualizeCamera2Activity;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageAccessException;
import boofcv.struct.image.ImageType;

public class MainActivity extends VisualizeCamera2Activity {

    private FrameLayout container;
    private FrameLayout canvas;
    private FrameLayout result;

    private ImageView decoded;

    private GrayU8 image;
    private GrayU8 matrix;

    private Bitmap bitmap;

    private GuideCanvas guide;

    private String data;

    private int resultScale;
    private int matrixSize = 8;

    public MainActivity(){
        targetResolution = 1366*768;
    }

    public void scan(){

        Thread parser = new Thread() {

            @Override
            public void run() {

                GrayU8 scanned = image.clone();

                if(image == null) { return; }

                matrix = new GrayU8(matrixSize, matrixSize);

                /*
                for(int y=0; y<guide.length-1; y++){

                    for(int x=0; x<guide.length-1; x++){

                        int value = scanned.get(guide.xStart + x + 1, guide.yStart + y + 1);
                        value = (value>127)? 255: 0;
                        matrix.set(x, y, value);
                    }
                }
                */

                for(int y=0; y<matrix.getHeight(); y++){

                    for(int x=0; x<matrix.getWidth(); x++){

                        int step = (int)(guide.length/(1.0*guide.steps));
                        int value = scanned.get(guide.xStart + (int)(step/2.0) + x*step, guide.yStart + (int)(step/2.0) + y*step);
                        value = (value>127)? 255: 0;
                        matrix.set(x, y, value);
                    }
                }

                ImageMiscOps.rotateCW(matrix);

                /*
                GrayU8 mini = new GrayU8(guide.steps, guide.steps);
                FDistort distort = new FDistort();
                distort.init(matrix, mini);
                distort.scale();
                distort.apply();
                */

                bitmap = Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.RGB_565);
                ConvertBitmap.boofToBitmap(matrix, bitmap, null);
                bitmap = Bitmap.createScaledBitmap(bitmap,bitmap.getWidth() * resultScale, bitmap.getHeight() * resultScale, false);

                data = decode(matrix);
                updateResults();
            }
        };

        parser.start();
    }

    private String decode(GrayU8 matrix){

        StringBuilder result = new StringBuilder();

        int byteIterator = 0;
        int currentByte = 0;

        for(int i=0; i<matrix.getHeight(); i++){

            for(int j=0; j<matrix.getWidth(); j++){

                if(byteIterator >= 8){

                    byteIterator = 0;
                    currentByte = 0;
                }

                int b = matrix.get(j,i);
                currentByte = currentByte + ( (b<127) ? (int)Math.pow(2, 7-byteIterator) : 0 );
                byteIterator++;
            }

            result.append((char)currentByte);
        }

        return result.toString();
    }

    private static class GuideCanvas{

        int length, steps;
        int xStart, xEnd, yStart, yEnd;

        private GuideCanvas(int width, int height, int steps){

            xStart = (int) (width / 3.0);
            xEnd = width - xStart;

            length = xEnd - xStart;

            yStart = (int) (height / 2.0 - length / 2.0);
            yEnd = yStart + length;

            this.steps = steps;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultScale = (int)(448 / (1.0 * matrixSize) );

        canvas = findViewById(R.id.canvas);

        ImageView scanButton = findViewById(R.id.scan_button);
        scanButton.setOnClickListener(v -> scan());

        container = findViewById(R.id.container);
        container.bringChildToFront(canvas);

        result = findViewById(R.id.result);

        decoded = findViewById(R.id.decoded);
        decoded.setOnClickListener(v -> container.bringChildToFront(canvas));

        guide = new GuideCanvas(480, 640, matrixSize);

        setImageType(ImageType.single(GrayU8.class));

        startCamera(canvas, null);
    }

    private void drawGuide(GrayU8 image, GuideCanvas guide){

        for(int x=guide.xStart; x<=guide.xEnd; x++){

            try{
                image.set(x, guide.yStart, 255);
                image.set(x, guide.yEnd, 255);
            } catch(ImageAccessException iae){
                System.out.println("tried to draw the guide out of bound");
            }
        }

        for(int y=guide.yStart; y<=guide.yEnd; y++){

            try{
                image.set(guide.xStart, y, 255);
                image.set(guide.xEnd, y, 255);
            } catch(ImageAccessException iae){
                System.out.println("tried to draw the guide out of bound");
            }
        }
    }

    private void updateResults(){

        runOnUiThread(() -> {

            decoded.setImageBitmap(bitmap);
            container.bringChildToFront(result);

            Toast.makeText(MainActivity.this, data, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onCameraResolutionChange( int width , int height, int sensorOrientation ) {
        super.onCameraResolutionChange(width, height,sensorOrientation);

        guide = new GuideCanvas(width, height, matrixSize);
    }

    @Override
    protected void processImage(boofcv.struct.image.ImageBase image) {

        this.image = (GrayU8) image;
        drawGuide((GrayU8) image, guide);
    }
}

<filename>Word2VecfJava-master/src/main/java/com/isaac/word2vec/CNNSentenceClassification.java<fim_prefix>

        package com.isaac.word2vec;


import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.deeplearning4j.iterator.CnnSentenceDataSetIterator;
import org.deeplearning4j.iterator.LabeledSentenceProvider;
import org.deeplearning4j.iterator.provider.FileLabeledSentenceProvider;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.GlobalPoolingLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.conf.layers.PoolingType;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 *
 * Convolutional Neural Networks for Sentence Classification - https://arxiv.org/abs/1408.5882
 * @author Alex Black
 *
 * Modified by zhanghao on 01/11/17.
 * @author  ZHANG HAO
 * email: isaac.changhau@gmail.com
 */
@SuppressWarnings("all")
public class CNNSentenceClassification {

    private static final int BUFFER_SIZE = 4096;

    public static ComputationGraph buildCNNGraph (int vectorSize, int cnnLayerFeatureMaps, PoolingType globalPoolingType) {
        // Set up the network configuration. Note that we have multiple convolution layers, each wih filter
        // widths of 3, 4 and 5 as per Kim (2014) paper.
        ComputationGraphConfiguration config = new NeuralNetConfiguration.Builder()
                .weightInit(WeightInit.RELU)
                .activation(Activation.LEAKYRELU)
                .updater(Updater.ADAM)
                .convolutionMode(ConvolutionMode.Same)      //This is important so we can 'stack' the results later
                .regularization(true).l2(0.0001)
                .learningRate(0.01)
                .graphBuilder()
                .addInputs("input")
                .addLayer("cnn3", new ConvolutionLayer.Builder()
                        .kernelSize(3, vectorSize)
                        .stride(1, vectorSize)
                        .nIn(1)
                        .nOut(cnnLayerFeatureMaps)
                        .build(), "input")
                .addLayer("cnn4", new ConvolutionLayer.Builder()
                        .kernelSize(4, vectorSize)
                        .stride(1, vectorSize)
                        .nIn(1)
                        .nOut(cnnLayerFeatureMaps)
                        .build(), "input")
                .addLayer("cnn5", new ConvolutionLayer.Builder()
                        .kernelSize(5, vectorSize)
                        .stride(1, vectorSize)
                        .nIn(1)
                        .nOut(cnnLayerFeatureMaps)
                        .build(), "input")
                //Perform depth concatenation
                .addVertex("merge", new MergeVertex(), "cnn3", "cnn4", "cnn5")
                .addLayer("globalPool", new GlobalPoolingLayer.Builder()
                        .poolingType(globalPoolingType)
                        .build(), "merge")
                .addLayer("out", new OutputLayer.Builder()
                        .lossFunction(LossFunctions.LossFunction.MCXENT)
                        .activation(Activation.SOFTMAX)
                        .nIn(3 * cnnLayerFeatureMaps)
                        .nOut(2)    //2 classes: positive or negative
                        .build(), "globalPool")
                .setOutputs("out")
                .build();

        ComputationGraph net = new ComputationGraph(config);
        net.init();
        return net;
    }

    public static DataSetIterator getDataSetIterator(String DATA_PATH, boolean isTraining, WordVectors wordVectors, int minibatchSize,
                                                     int maxSentenceLength, Random rng ){
        String path = FilenameUtils.concat(DATA_PATH, (isTraining ? "aclImdb/train/" : "aclImdb/test/"));
        String positiveBaseDir = FilenameUtils.concat(path, "pos");
        String negativeBaseDir = FilenameUtils.concat(path, "neg");

        File filePositive = new File(positiveBaseDir);
        File fileNegative = new File(negativeBaseDir);

        Map<String,List<File>> reviewFilesMap = new HashMap<>();
        reviewFilesMap.put("Positive", Arrays.asList(filePositive.listFiles()));
        reviewFilesMap.put("Negative", Arrays.asList(fileNegative.listFiles()));

        LabeledSentenceProvider sentenceProvider = new FileLabeledSentenceProvider(reviewFilesMap, rng);

        return new CnnSentenceDataSetIterator.Builder()
                .sentenceProvider(sentenceProvider)
                .wordVectors(wordVectors)
                .minibatchSize(minibatchSize)
                .maxSentenceLength(maxSentenceLength)
                .useNormalizedWordVectors(false)
                .build();
    }

    public static void aclImdbDownloader(String DATA_URL, String DATA_PATH) {
        try {
            //Create directory if required
            File directory = new File(DATA_PATH);
            if (!directory.exists()) directory.mkdir();
            //Download file:
            String archivePath = DATA_PATH + "aclImdb_v1.tar.gz";
            File archiveFile = new File(archivePath);
            String extractedPath = DATA_PATH + "aclImdb";
            File extractedFile = new File(extractedPath);
        <fim_suffix>
            if (!archiveFile.exists()) {
                System.out.println("Starting data download (80MB)...");
                FileUtils.copyURLToFile(new URL(DATA_URL), archiveFile);
                System.out.println("Data (.tar.gz file) downloaded to " + archiveFile.getAbsolutePath());
                //Extract tar.gz file to output directory
                extractTarGz(archivePath, DATA_PATH);
            } else {
                //Assume if archive (.tar.gz) exists, then data has already been extracted
                System.out.println("Data (.tar.gz file) already exists at " + archiveFile.getAbsolutePath());
                if (!extractedFile.exists()) {
                    //Extract tar.gz file to output directory
                    extractTarGz(archivePath, DATA_PATH);
                } else {
                    System.out.println("Data (extracted) already exists at " + extractedFile.getAbsolutePath());
                }
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    private static void extractTarGz(String filePath, String outputPath) throws IOException {
        int fileCount = 0;
        int dirCount = 0;
        System.out.print("Extracting files");
        try(TarArchiveInputStream tais = new TarArchiveInputStream(
                new GzipCompressorInputStream( new BufferedInputStream( new FileInputStream(filePath))))){
            TarArchiveEntry entry;

            /* Read the tar entries using the getNextEntry method */
            while ((entry = (TarArchiveEntry) tais.getNextEntry()) != null) {
                //System.out.println("Extracting file: " + entry.getName());

                //Create directories as required
                if (entry.isDirectory()) {
                    new File(outputPath + entry.getName()).mkdirs();
                    dirCount++;
                }else {
                    int count;
                    byte data[] = new byte[BUFFER_SIZE];

                    FileOutputStream fos = new FileOutputStream(outputPath + entry.getName());
                    BufferedOutputStream dest = new BufferedOutputStream(fos,BUFFER_SIZE);
                    while ((count = tais.read(data, 0, BUFFER_SIZE)) != -1) {
                        dest.write(data, 0, count);
                    }
                    dest.close();
                    fileCount++;
                }
                if(fileCount % 1000 == 0) System.out.print(".");
            }
        }
        System.out.println("\n" + fileCount + " files and " + dirCount + " directories extracted to: " + outputPath);
    }

}
<fim_middle>
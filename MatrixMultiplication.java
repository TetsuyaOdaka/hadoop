package com.tetsuyaodaka.hadoop.math.matrix;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 *　 Matrix Multiplication on Hadoop Map Reduce　(Case1)
 *
 *   author : tetsuya.odaka@gmail.com
 *   tested on Hadoop1.2 
 *   
 *   Split the Large Scale Matrix to SubMatrices.
 *   Split size (Number Of Rows or Columns) can be specified by arguments.
 *   
 *   This should be decided according to your resources.
 *   Partitioner and Conditioner are not implemented here.
 *   Can calculate real numbers (format double) and be expected.
 *   
 *   This program is distributed under ASF2.0 LICENSE.
 * 
 */
public class MatrixMultiplication {

	/*
	 *  IndexPair Class
	 *
	 *　customized key for reduce function consists of row BlockNum of MatrixA, MatrixB
	 *
	 */
	public static class IndexPair implements WritableComparable<MatrixMultiplication.IndexPair> {
		public int index1;
		public int index2;
		
		public IndexPair() {
		}
		
		public IndexPair(int index1, int index2) {
			this.index1 = index1;
			this.index2 = index2;
		}

		public void write (DataOutput out)
			throws IOException
		{
			out.writeInt(index1);
			out.writeInt(index2);
		}
		
		public void readFields (DataInput in)
			throws IOException
		{
			index1 = in.readInt();
			index2 = in.readInt();
	
		}

		public int compareTo(MatrixMultiplication.IndexPair o) {
			if (this.index1 < o.index1) {
				return -1;
			} else if (this.index1 > o.index1) {
				return +1;
			}
			if (this.index2 < o.index2) {
				return -1;
			} else if (this.index2 > o.index2) {
				return +1;
			}
			return 0;
		}
		
		/*
		 * hasHash() is used by HashPartitioner.
		 */
		public int hashCode(){
			int ib = this.index1;
			int jb = this.index2;
			int num = ib * Integer.MAX_VALUE + jb;
			int hash = new Integer(num).hashCode();
			return Math.abs(hash);
		}
	}
	
	/*
	 *  MapA Class
	 *
	 *  read MatrixA and decompose its elements to blocks
	 *
	 */
    public static class MapA extends Mapper<LongWritable, Text, MatrixMultiplication.IndexPair, Text>{
        @Override
        protected void map(LongWritable key, Text value, Context context) 
        		throws IOException, InterruptedException{

        	String strArr[] = value.toString().split("\t");
        	String keyArr[] = strArr[0].split(" ");
        	int nor= Integer.parseInt(keyArr[0]);	// number of row
        	String noc= keyArr[1];	// number of column
        	String v= strArr[1];	// value

            int m = 0;

            // retrieve from configuration
            int IB 	= Integer.parseInt(context.getConfiguration().get("IB"));	// row block size
            int N 	= Integer.parseInt(context.getConfiguration().get("N"));	// number of block of MatrixB

            if(nor%IB == 0){
            	m = nor/IB; 
            }else{
            	m = nor/IB + 1;             	
            }
            for(int j=1;j<(N+1);j++){
            	context.write(new MatrixMultiplication.IndexPair(m,j), new Text("0"+","+nor+","+noc+","+v));
            }
        }
    }

	/*
	 * MapB Class
	 * 
	 *  read MatrixB and decompose it to blocks
	 *
	 */
    public static class MapB extends Mapper<LongWritable, Text, MatrixMultiplication.IndexPair, Text>{
        @Override
        protected void map(LongWritable key, Text value, Context context) 
        		throws IOException, InterruptedException{

        	String strArr[] = value.toString().split("\t");
        	String keyArr[] = strArr[0].split(" ");
        	int noc= Integer.parseInt(keyArr[1]);	// number of row
        	String nor= keyArr[0];	// number of column
        	String v= strArr[1];	// value

        	int n = 0;

            // retrieve from configuration
            int KB 	= Integer.parseInt(context.getConfiguration().get("KB"));
            int M 	= Integer.parseInt(context.getConfiguration().get("M"));
  
            if(noc%KB == 0){
            	n = noc/KB; 
            }else{
            	n = noc/KB + 1;             	
            }
            for(int j=1;j<(M+1);j++){
            	context.write(new MatrixMultiplication.IndexPair(j,n),new Text("1"+","+nor+","+noc+","+v));
            }
        }
    }
    
	/*
	 * Reduce Class
	 * 
	 */
    public static class Reduce extends Reducer<MatrixMultiplication.IndexPair, Text, Text, DoubleWritable>{

		@Override
        protected void reduce(MatrixMultiplication.IndexPair key, Iterable<Text> values, Context context) 
        		throws IOException, InterruptedException{

    		Map<String,String> aMap = new HashMap<String,String>();
    		Map<String,String> bMap = new HashMap<String,String>();
    		
			int rCount=0;
        	for(Text value: values){
            	String strVal = value.toString();
            	String[] strArray = strVal.split(",");

            	if(Integer.parseInt(strArray[0])==0) {
            		aMap.put(strArray[1]+" "+strArray[2],strArray[3]);
            		rCount++;

            	}else{
            		bMap.put(strArray[2]+" "+strArray[1],strArray[3]);
        		}
        	}
        	
            int is = Integer.parseInt(context.getConfiguration().get("IB"));
            int js = Integer.parseInt(context.getConfiguration().get("KB"));

            int im = Integer.parseInt(context.getConfiguration().get("I"));
            int jm = Integer.parseInt(context.getConfiguration().get("K"));

            
            int startI = (key.index1-1)*is+1;
            int endI = (key.index1)*is;
            if(endI>im) endI=im;
            int startJ = (key.index2-1)*js+1;
            int endJ = (key.index2)*js;
            if(endJ>jm) endJ=jm;

            rCount=rCount/(endI-startI+1);
            
            for(int i=startI;i<endI+1;i++){
                for(int j=startJ;j<endJ+1;j++){
                	double sum=0;
                	for(int k=1;k<rCount+1;k++){
                        sum+=Double.parseDouble(aMap.get(i+" "+k))*Double.parseDouble(bMap.get(j+" "+k));
                	}
                    BigDecimal bd = new BigDecimal(sum);
        			BigDecimal r = bd.setScale(2, BigDecimal.ROUND_HALF_UP); 
                	context.write(new Text(i + " " + j), new DoubleWritable(r.doubleValue()));
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, ClassNotFoundException {

    	Date startProc = new Date(System.currentTimeMillis());
    	System.out.println("process started at " + startProc);
    	
    	Configuration conf = new Configuration();
        int I = Integer.parseInt(args[3]); // Num of Row of MatrixA
		int K = Integer.parseInt(args[4]); // Num of Row of MatrixB'

		int IB = Integer.parseInt(args[5]); // RowBlock Size of MatrixA
		int KB = Integer.parseInt(args[6]); // RowBlock Size of MatrixB'
		
		int M =0;
		if(I%IB == 0){
			M = I/IB;
		}else{
			M = I/IB+1;
		}

		int N =0;
		if(K%KB == 0){
			N = K/KB;
		}else{
			N = K/KB+1;
		}
		
    	conf.set("I", args[3]); // Num of Row of MatrixA
    	conf.set("K", args[4]); // Num of Row of MatrixB'
    	conf.set("IB", args[5]); // RowBlock Size of MatrixA
    	conf.set("KB", args[6]); // RowBlock Size of MatrixB'
    	conf.set("M", new Integer(M).toString());
    	conf.set("N", new Integer(N).toString());
    	
    	Job job = new Job(conf, "MatrixMultiplication");
        job.setJarByClass(MatrixMultiplication.class);

        job.setReducerClass(Reduce.class);

        job.setMapOutputKeyClass(MatrixMultiplication.IndexPair.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        // Mapperごとに読み込むファイルを変える。
        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, MapA.class); // matrixA
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, MapB.class); // matrixB
        FileOutputFormat.setOutputPath(job, new Path(args[2])); // output path
        
		System.out.println("num of MatrixA RowBlock(M) is "+M);
		System.out.println("num of MatrixB ColBlock(N) is "+N);

		boolean success = job.waitForCompletion(true);

    	Date endProc = new Date(System.currentTimeMillis());
    	System.out.println("process ended at " + endProc);

    	System.out.println(success);
    }
}

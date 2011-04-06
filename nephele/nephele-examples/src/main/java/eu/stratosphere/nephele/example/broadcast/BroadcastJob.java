/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.nephele.example.broadcast;

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.stratosphere.nephele.client.JobClient;
import eu.stratosphere.nephele.client.JobExecutionException;
import eu.stratosphere.nephele.configuration.ConfigConstants;
import eu.stratosphere.nephele.configuration.Configuration;
import eu.stratosphere.nephele.configuration.GlobalConfiguration;
import eu.stratosphere.nephele.fs.Path;
import eu.stratosphere.nephele.io.channels.ChannelType;
import eu.stratosphere.nephele.io.compression.CompressionLevel;
import eu.stratosphere.nephele.jobgraph.JobFileInputVertex;
import eu.stratosphere.nephele.jobgraph.JobFileOutputVertex;
import eu.stratosphere.nephele.jobgraph.JobGraph;
import eu.stratosphere.nephele.jobgraph.JobGraphDefinitionException;
import eu.stratosphere.nephele.util.JarFileCreator;

/**
 * This class creates and submits a simple broadcast test job.
 * 
 * @author warneke
 */
public class BroadcastJob {

	/**
	 * The logging object used to report errors.
	 */
	private static final Log LOG = LogFactory.getLog(BroadcastJob.class);

	/**
	 * The entry point to the application.
	 * 
	 * @param args
	 *        the command line arguments, possibly containing the job manager address
	 */
	public static void main(String[] args) {

		String jobManagerAddress = null;

		if (args.length > 0) {
			jobManagerAddress = args[0];
		}

		
		// Construct job graph
		final JobGraph jobGraph = new JobGraph("Broadcast Job");

		final JobFileInputVertex producer = new JobFileInputVertex("Broadcast Producer", jobGraph);
		producer.setFileInputClass(BroadcastProducer.class);
		producer.setFilePath(new Path("file:///tmp/dummy/"));

		final JobFileOutputVertex consumer = new JobFileOutputVertex("Broadcast Consumer", jobGraph);
		consumer.setFileOutputClass(BroadcastConsumer.class);
		consumer.setFilePath(new Path("file:///tmp/dummy"));
		consumer.setNumberOfSubtasks(8);
		consumer.setVertexToShareInstancesWith(producer);

		try {
			producer.connectTo(consumer, ChannelType.NETWORK, CompressionLevel.NO_COMPRESSION);
		} catch (JobGraphDefinitionException e) {
			LOG.error(e);
			return;
		}

		// Create jar file and attach it
		final File jarFile = new File("/tmp/broadcastJob.jar");
		final JarFileCreator jarFileCreator = new JarFileCreator(jarFile);
		jarFileCreator.addClass(BroadcastProducer.class);
		jarFileCreator.addClass(BroadcastConsumer.class);
		jarFileCreator.addClass(BroadcastRecord.class);

		try {
			jarFileCreator.createJarFile();
			System.out.println("done creating!!");
		} catch (IOException ioe) {

			if (jarFile.exists()) {
				jarFile.delete();
			}

			System.out.println("ERROR creating jar");
			LOG.error(ioe);
			return;
		}

		jobGraph.addJar(new Path("file://" + jarFile.getAbsolutePath()));

		// Submit job
		Configuration conf = new Configuration();
		conf.setString(ConfigConstants.JOB_MANAGER_IPC_ADDRESS_KEY, "127.0.0.1");
		conf.setString(ConfigConstants.JOB_MANAGER_IPC_PORT_KEY, "6123");

		try {
			final JobClient jobClient = new JobClient(jobGraph, conf);
			System.out.println("submitting");
			jobClient.submitJobAndWait();
			System.out.println("done.");
		} catch (IOException e) {
			LOG.error(e);
		} catch (JobExecutionException e) {
			LOG.error(e);
		}

		if (jarFile.exists()) {
			jarFile.delete();
		}
	}
}

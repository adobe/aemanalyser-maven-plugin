package com.adobe.aem.analyser.mojos;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "simple-test")
public class SimpleMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        System.out.println("############ hello");
    }
}

package com.example.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CrossDomainController {

  private static final String CROSS_DOMAIN_XML = "static/crossdomain.xml";

  @GetMapping(value = "/crossdomain.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public String getCrossDomainXml() {
    try {
      ClassPathResource resource = new ClassPathResource(CROSS_DOMAIN_XML);
      return new String(Files.readAllBytes(Paths.get(resource.getURI())));
    } catch (IOException e) {
      return "<?xml version=\"1.0\"?>\n"
          + "<cross-domain-policy>\n"
          + "  <allow-access-from domain=\"*\" />\n"
          + "</cross-domain-policy>";
    }
  }
}

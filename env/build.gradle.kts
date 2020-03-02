plugins {
    id("com.cognifide.environment")
    id("com.cognifide.aem.instance.local")
}

repositories {
    jcenter()
    maven("https://dl.bintray.com/acs/releases")
}

aem {
    `package` {
        validator {
            base("com.adobe.acs:acs-aem-commons-oakpal-checks:4.4.0")
        }
    }

    instance {
        satisfier {
            packages {
                // "dep.vanity-urls"("pkg/vanityurls-components-1.0.2.zip")
                "dep.core-components-all"("com.adobe.cq:core.wcm.components.all:2.8.0@zip")
                "dep.core-components-examples"("com.adobe.cq:core.wcm.components.examples:2.8.0@zip")
                "tool.aem-easy-content-upgrade"("https://github.com/valtech/aem-easy-content-upgrade/releases/download/3.0.1/aecu.bundle-3.0.1.zip")
                "tool.ac-tool"("https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool", "accesscontroltool-package/2.3.2/accesscontroltool-package-2.3.2.zip", "accesscontroltool-oakindex-package/2.3.2/accesscontroltool-oakindex-package-2.3.2.zip")
                "tool.search-webconsole-plugin"("com.neva.felix:search-webconsole-plugin:1.3.0")
            }
        }
        provisioner {
            step("disable-unsecure-bundles") {
                condition { once() && instance.environment == "prod" }
                sync {
                    osgi.stopBundle("org.apache.sling.jcr.webdav")
                    osgi.stopBundle("com.adobe.granite.crxde-lite")

                    instance.awaitUp() // include above in property: 'instance.awaitUp.bundles.symbolicNamesIgnored'
                }
            }
            // https://github.com/Cognifide/gradle-aem-plugin/blob/master/docs/instance-plugin.md#task-instanceprovision
        }
    }

    localInstance {
        install {
            files {
                // https://github.com/Cognifide/gradle-aem-plugin/blob/master/docs/local-instance-plugin.md#pre-installed-osgi-bundles-and-crx-packages
            }
        }
    }

    tasks {
        environmentUp {
            mustRunAfter(instanceUp, instanceSatisfy, instanceProvision, instanceSetup)
        }
        environmentAwait {
            mustRunAfter(instanceAwait)
        }
    }
}

environment {
    docker {
        containers {
            "httpd" {
                resolve {
                    resolveFiles {
                        download("http://download.macromedia.com/dispatcher/download/dispatcher-apache2.4-linux-x86_64-4.3.3.tar.gz").use {
                            copyArchiveFile(it, "**/dispatcher-apache*.so", file("modules/mod_dispatcher.so"))
                        }
                    }
                    ensureDir("htdocs", "cache", "logs")
                }
                up {
                    ensureDir("/usr/local/apache2/logs", "/var/www/localhost/htdocs", "/var/www/localhost/cache")
                    execShell("Starting HTTPD server", "/usr/sbin/httpd -k start")
                }
                reload {
                    cleanDir("/var/www/localhost/cache")
                    execShell("Restarting HTTPD server", "/usr/sbin/httpd -k restart")
                }
                dev {
                    watchRootDir("app/aem/dispatcher/src/conf.d", "app/aem/dispatcher/src/conf.dispatcher.d")
                }
            }
        }
    }
    hosts {
        "http://author.example.com" { tag("author") }
        "http://example.com" { tag("publish") }
        "http://dispatcher.example.com" { tag("dispatcher") }
    }

    healthChecks {
        http("Live Site", "http://example.com/en-us.html","English US")
        http("Author module 'Site'", "http://author.example.com/sites.html") {
            containsText("Sites")
            options { basicCredentials = aem.authorInstance.credentials }
        }
    }
}

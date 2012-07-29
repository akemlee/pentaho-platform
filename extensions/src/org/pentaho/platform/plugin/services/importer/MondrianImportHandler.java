package org.pentaho.platform.plugin.services.importer;

/**
 * Used by REST Services to handle mulit part form upload from Schema WorkBench 
 * 
 * @author tband
 * @date 6/27/12
 * 
 */
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import mondrian.xmla.DataSourcesConfig.DataSource;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.metadata.repository.DomainAlreadyExistsException;
import org.pentaho.metadata.repository.DomainIdNullException;
import org.pentaho.metadata.repository.DomainStorageException;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.engine.core.system.PentahoSessionHolder;
import org.pentaho.platform.plugin.action.mondrian.catalog.IMondrianCatalogService;
import org.pentaho.platform.plugin.action.mondrian.catalog.MondrianCatalog;
import org.pentaho.platform.plugin.action.mondrian.catalog.MondrianCatalogServiceException;
import org.pentaho.platform.plugin.action.mondrian.catalog.MondrianCatalogServiceException.Reason;
import org.pentaho.platform.plugin.action.mondrian.catalog.MondrianDataSource;
import org.pentaho.platform.plugin.action.mondrian.catalog.MondrianSchema;
import org.xml.sax.SAXException;

public class MondrianImportHandler implements IPlatformImportHandler {

  private static final Log logger = LogFactory.getLog(MondrianImportHandler.class);

  IMondrianCatalogService mondrianRepositoryImporter;

  public MondrianImportHandler(final IMondrianCatalogService mondrianImporter) {
    if (mondrianImporter == null) {
      throw new IllegalArgumentException();
    }
    this.mondrianRepositoryImporter = mondrianImporter;
  }

  /**
   * ****************************************
   * Main entry point from the Spring Interface
   * @param IPlatformImportBundle
   * @throws IOException 
   * @throws DomainStorageException 
   * @throws DomainAlreadyExistsException 
   * @throws DomainIdNullException 
   * @throws PlatformImportException 
   * @throws SAXException 
   * @throws ParserConfigurationException 
   */

  public void importFile(IPlatformImportBundle bundle) throws PlatformImportException, DomainIdNullException,
      DomainAlreadyExistsException, DomainStorageException, IOException {
    boolean overwriteInRepossitory = bundle.overwriteInRepossitory();
    boolean xmla = ("True".equals(bundle.getProperty("enableXmla"))) ? true : false;
    logger.debug("Importing as metadata - [domain=" + bundle.getName() + "]");
    logger.debug("importFile start " + bundle.getName() + " overwriteInRepossitory:" + overwriteInRepossitory);
    final String domainId = (String) bundle.getProperty("domain-id");

    if (domainId == null) {
      throw new PlatformImportException("Bundle missing required domain-id property");
    }
    try {
      String ds = (String) bundle.getProperty("DataSource");
      MondrianCatalog catalog = this.createCatalogObject(domainId, ds, xmla);
      mondrianRepositoryImporter.addCatalog(catalog, overwriteInRepossitory,
          PentahoSessionHolder.getSession());
    } catch (MondrianCatalogServiceException mse) {
      int statusCode = convertExceptionToStatus(mse);
      throw new PlatformImportException(mse.getMessage(), statusCode);
    } catch (Exception e) {
      throw new PlatformImportException(e.getMessage(), PlatformImportException.PUBLISH_GENERAL_ERROR);
    }
  }

  /**
   * convert the catalog service exception to a platform exception and get the proper status code
   * @param mse
   * @return
   */
  private int convertExceptionToStatus(MondrianCatalogServiceException mse) {
    int statusCode = PlatformImportException.PUBLISH_TO_SERVER_FAILED;
    if (mse.getReason().equals(Reason.GENERAL)) {
      statusCode = PlatformImportException.PUBLISH_GENERAL_ERROR;
    } else {
      if (mse.getReason().equals(Reason.ACCESS_DENIED)) {
        statusCode = PlatformImportException.PUBLISH_TO_SERVER_FAILED;
      } else {
        if (mse.getReason().equals(Reason.ALREADY_EXISTS)) {
          statusCode = PlatformImportException.PUBLISH_SCHEMA_EXISTS_ERROR;
        }
      }
    }
    return statusCode;
  }

  /**
   * Helper method to create a catalog object 
   * @param domainId
   * @param datasource
   * @return
   * @throws ParserConfigurationException
   * @throws SAXException
   * @throws IOException
   */
  protected MondrianCatalog createCatalogObject(String domainId, String datasource, boolean xmlaEnabled)
      throws ParserConfigurationException, SAXException, IOException {

    String catName = domainId;
    MondrianSchema schema = new MondrianSchema(catName, null);
    String dsProvider = xmlaEnabled ? DataSource.PROVIDER_TYPE_MDP : "None:";
    MondrianDataSource ds = new MondrianDataSource(catName, "", "", "Provider=mondrian;DataSource=" + datasource,
        "Provider=Mondrian", dsProvider, DataSource.AUTH_MODE_UNAUTHENTICATED, null);

    String FILE_SEPARTOR = RepositoryFile.SEPARATOR;
    MondrianCatalog catalog = new MondrianCatalog(catName, "Provider=mondrian;DataSource=" + datasource + ";",
        "mondrian:" + FILE_SEPARTOR + catName, ds, schema);
    return catalog;
  }

}

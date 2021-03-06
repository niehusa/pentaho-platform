/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2018 Hitachi Vantara..  All rights reserved.
 */

package org.pentaho.platform.plugin.services.importer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.PropertyResourceBundle;

import org.apache.commons.lang.StringUtils;
import org.pentaho.platform.api.repository2.unified.IPlatformImportBundle;
import org.pentaho.platform.api.mimetype.IPlatformMimeResolver;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.plugin.services.importexport.ExportFileNameEncoder;
import org.pentaho.platform.plugin.services.importexport.ImportSession;
import org.pentaho.platform.plugin.services.importexport.ImportSource.IRepositoryFileBundle;
import org.pentaho.platform.repository.RepositoryFilenameUtils;

/**
 * this class is used to handle .properties files that are XACTION or URL files that contain the metadata used for
 * localization. These files may contain additional information that will allow the properties file to be stored and
 * used by XACTION and URL as well as localize the title and description.
 * 
 * @author tband /ezequiel / tkafalas
 * 
 */
public class LocaleFilesProcessor {

  private static final String FILE_LOCALE_RESOLVER = "file.locale";
  private static final String URL_DESCRIPTION = "url_description";
  private static final String URL_NAME = "url_name";
  private static final String DESCRIPTION = "description";
  private static final String TITLE = "title";
  private static final String NAME = "name";
  private static final String PROPERTIES_EXT = ".properties";
  private static final String LOCALE_EXT = ".locale";
  private List<LocaleFileDescriptor> localeFiles;

  public LocaleFilesProcessor() {
    localeFiles = new ArrayList<LocaleFileDescriptor>();
  }

  /**
   * 
   * @param file
   * @param parentPath
   * @param bytes
   * @return false - means discard the file extension type
   * @throws IOException
   */
  public boolean isLocaleFile( IRepositoryFileBundle file, String parentPath, byte[] bytes ) throws IOException {

    boolean isLocale = false;
    String fileName = file.getFile().getName();
    String actualFilePath = file.getPath();
    RepositoryFile localeRepositoryFile = file.getFile();
    if ( ImportSession.getSession().getManifest() != null && ImportSession.getSession().getManifest().getManifestInformation().getManifestVersion() != null ) {
      fileName = ExportFileNameEncoder.decodeZipFileName( fileName );
      actualFilePath = ExportFileNameEncoder.decodeZipFileName( actualFilePath );
      localeRepositoryFile = new RepositoryFile.Builder( localeRepositoryFile ).name(
          ExportFileNameEncoder.decodeZipFileName( localeRepositoryFile.getName() ) ).build();
    }
    int sourceVersion = 0; // 0 = Not a local file, 1 = 4.8 .properties file, 2= Sugar 5.0 .local file
    if ( fileName.endsWith( PROPERTIES_EXT ) ) {
      sourceVersion = 1;
    } else if ( fileName.endsWith( LOCALE_EXT ) ) {
      sourceVersion = 2;
    }
    if ( sourceVersion != 0 ) {
      InputStream inputStream = new ByteArrayInputStream( bytes );
      Properties properties = loadProperties( inputStream );

      String name = getProperty( properties, NAME, sourceVersion );
      String title = getProperty( properties, TITLE, sourceVersion );
      String description = getProperty( properties, DESCRIPTION, sourceVersion );
      String url_name = getProperty( properties, URL_NAME, sourceVersion );
      String url_description = getProperty( properties, URL_DESCRIPTION, sourceVersion );

      if ( !StringUtils.isEmpty( url_name ) ) {
        name = url_name;
      }
      if ( !StringUtils.isEmpty( title ) ) {
        name = title;
      }

      description = !StringUtils.isEmpty( description ) ? description : "";
      if ( !StringUtils.isEmpty( url_description ) ) {
        description = url_description;
      }

      if ( !StringUtils.isEmpty( name ) ) {
        String filePath = ( actualFilePath.equals( "/" ) || actualFilePath.equals( "\\" ) ) ? "" : actualFilePath;
        filePath = RepositoryFilenameUtils.concat( parentPath, filePath );
        LocaleFileDescriptor localeFile;
        switch ( sourceVersion ) {
          case 1:
            localeFile = new LocaleFileDescriptor( name, PROPERTIES_EXT, description, filePath, localeRepositoryFile,
              inputStream );
            break;
          case 2:
            localeFile =
              new LocaleFileDescriptor( name, LOCALE_EXT, description, filePath, localeRepositoryFile, inputStream );
            break;
          default:
            localeFile = new LocaleFileDescriptor( name, description, filePath, localeRepositoryFile, inputStream );
        }
        localeFiles.add( localeFile );

        /**
         * assumes that the properties file has additional localization attributes and should be imported
         */
        if ( properties.size() <= 2 || sourceVersion == 2 ) {
          isLocale = true;
        }
      }
    }
    return isLocale;
  }

  private String getProperty( Properties properties, String propertyName, int sourceVersion ) {
    if ( sourceVersion == 1 ) {
      return properties.getProperty( propertyName );
    } else {
      return properties.getProperty( "file." + propertyName );
    }
  }

  public Properties loadProperties( InputStream inputStream ) throws IOException {
    assert inputStream != null;
    final Properties properties = new Properties();
    final PropertyResourceBundle rb = new PropertyResourceBundle( inputStream );
    final Enumeration<?> keyEnum = rb.getKeys();
    while ( keyEnum.hasMoreElements() ) {
      final Object key = keyEnum.nextElement();
      assert key != null;
      final String sKey = String.valueOf( key );
      properties.put( sKey, rb.getObject( sKey ) );
    }
    return properties;
  }

  public boolean createLocaleEntry( String filePath, String name, String title, String description,
      RepositoryFile file, InputStream is ) throws IOException {
    return createLocaleEntry( filePath, name, title, description, file, is, 2 );
  }

  public boolean createLocaleEntry( String filePath, String name, String title, String description,
      RepositoryFile file, InputStream is, int sourceVersion ) throws IOException {

    boolean success = false;
    // need to spoof the locales to think this is the actual parent .prpt and not the meta.xml
    RepositoryFile.Builder rf = new RepositoryFile.Builder( name );
    rf.path( filePath );
    if ( !StringUtils.isEmpty( title ) ) {
      name = title;
    }

    if ( !StringUtils.isEmpty( name ) ) {
      LocaleFileDescriptor localeFile;
      if ( name.endsWith( PROPERTIES_EXT ) ) {
        localeFile = new LocaleFileDescriptor( name, PROPERTIES_EXT, description, filePath, rf.build(), is );
      } else if ( name.endsWith( LOCALE_EXT ) ) {
        localeFile = new LocaleFileDescriptor( name, LOCALE_EXT, description, filePath, rf.build(), is );
      } else {
        localeFile = new LocaleFileDescriptor( name, description, filePath, rf.build(), is );
      }

      localeFiles.add( localeFile );

      success = true;

    }
    return success;
  }

  public void processLocaleFiles( IPlatformImporter importer ) throws PlatformImportException {
    RepositoryFileImportBundle.Builder bundleBuilder = new RepositoryFileImportBundle.Builder();
    IPlatformMimeResolver mimeResolver = PentahoSystem.get( IPlatformMimeResolver.class );
    String mimeType = mimeResolver.resolveMimeForFileName( FILE_LOCALE_RESOLVER );

    List<LocaleFileDescriptor> localeFilesToProceed = new ArrayList<>();

    for ( LocaleFileDescriptor localeFile : localeFiles ) {
      String extension = localeFile.getExtension();
      if ( !StringUtils.isEmpty( extension ) && extension.equals( LOCALE_EXT ) ) {
        localeFilesToProceed.add( localeFile );
      }
    }

    if ( !localeFilesToProceed.isEmpty() ) {
      for ( LocaleFileDescriptor localeFile : localeFilesToProceed ) {
        proceed( importer, bundleBuilder, mimeType, localeFile );
      }
    } else {
      for ( LocaleFileDescriptor localeFile : localeFiles ) {
        proceed( importer, bundleBuilder, mimeType, localeFile );
      }
    }

  }

  private void proceed( IPlatformImporter importer, RepositoryFileImportBundle.Builder bundleBuilder, String mimeType,
                        LocaleFileDescriptor localeFile ) throws PlatformImportException {
    bundleBuilder.name( localeFile.getName() );
    bundleBuilder.comment( localeFile.getDescription() );
    bundleBuilder.path( localeFile.getPath() );
    bundleBuilder.file( localeFile.getFile() );
    bundleBuilder.input( localeFile.getInputStream() );
    bundleBuilder.mime( mimeType );
    IPlatformImportBundle platformImportBundle = bundleBuilder.build();
    importer.importFile( platformImportBundle );
  }
}

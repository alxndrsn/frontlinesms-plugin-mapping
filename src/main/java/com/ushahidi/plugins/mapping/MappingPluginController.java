package com.ushahidi.plugins.mapping;

import com.ushahidi.plugins.mapping.data.repository.CategoryDao;
import com.ushahidi.plugins.mapping.data.repository.ContactLocationDao;
import com.ushahidi.plugins.mapping.data.repository.LocationDao;
import com.ushahidi.plugins.mapping.data.repository.IncidentDao;
import com.ushahidi.plugins.mapping.data.repository.MappingSetupDao;
import com.ushahidi.plugins.mapping.ui.MappingUIController;
import com.ushahidi.plugins.mapping.util.MappingLogger;

import net.frontlinesms.FrontlineSMS;
import net.frontlinesms.data.repository.ContactDao;
import net.frontlinesms.plugins.BasePluginController;
import net.frontlinesms.plugins.PluginControllerProperties;
import net.frontlinesms.plugins.PluginInitialisationException;
import net.frontlinesms.ui.UiGeneratorController;

import org.springframework.context.ApplicationContext;

/**
 * 
 * @author ekala
 *
 */
@PluginControllerProperties(name="Mapping (Beta)", iconPath="/icons/map.png", i18nKey = "plugins.ushahidi",
        springConfigLocation="classpath:com/ushahidi/plugins/mapping/mapping-spring-hibernate.xml",
        hibernateConfigPath="classpath:com/ushahidi/plugins/mapping/mapping.hibernate.cfg.xml")
public class MappingPluginController extends BasePluginController {
    private static MappingLogger LOG = MappingLogger.getLogger(MappingPluginController.class);	

    private FrontlineSMS frontlineController;
    private ApplicationContext applicationContext;
    private MappingUIController mappingUIController;
	
	private ContactDao contactDao;
    private CategoryDao categoryDao;
    private LocationDao locationDao;
    private IncidentDao incidentDao;
    private MappingSetupDao mappingSetupDao;
    private ContactLocationDao contactLocationDao;
    
    public String getHibernateConfigPath() {
        return "classpath:com/ushahidi/plugins/mapping/mapping.hibernate.cfg.xml";
    }

    public String getSpringConfigPath() {
        return "classpath:com/ushahidi/plugins/mapping/mapping-spring-hibernate.xml";
    }

    public void init(FrontlineSMS frontlineController, ApplicationContext applicationContext) throws PluginInitialisationException {
        this.frontlineController = frontlineController;
        this.applicationContext = applicationContext;
       
        try{
        	this.contactDao = frontlineController.getContactDao();
        	this.locationDao = (LocationDao)applicationContext.getBean("locationDao");
        	this.incidentDao = (IncidentDao)applicationContext.getBean("incidentDao");
        	this.mappingSetupDao = (MappingSetupDao)applicationContext.getBean("mappingSetupDao");
        	this.categoryDao = (CategoryDao)applicationContext.getBean("categoryDao");
        	this.contactLocationDao = (ContactLocationDao)applicationContext.getBean("contactLocationDao");
        }
        catch(Throwable t){
        	LOG.error("Unable to initialize dao objects");
            throw new PluginInitialisationException(t);
        }
    }

    public void init(FrontlineSMS frontlineController) {
        this.frontlineController = frontlineController;
    }

    public void deinit() {
    	if (mappingUIController != null) {
            mappingUIController.shutdownUIController();
    	}
    }

    public Object initThinletTab(UiGeneratorController uiController) {
        mappingUIController = new MappingUIController(this, frontlineController, uiController);         
        mappingUIController.initUIController();
        return mappingUIController.getTab();
    }

    public LocationDao getLocationDao(){
        return locationDao;
    }

    public CategoryDao getCategoryDao(){
        return categoryDao;
    }

    public IncidentDao getIncidentDao(){
        return incidentDao;
    }

    public MappingSetupDao getMappingSetupDao(){
        return mappingSetupDao;
    }
    
    public ContactLocationDao getContactLocationDao(){
        return contactLocationDao;
    }
    
    public ContactDao getContactDao() {
    	return contactDao;
    }
    
    public void showIncidentMap() {
    	mappingUIController.showIncidentMap();
    }
    
    public void refreshIncidentMap() {
    	mappingUIController.refreshIncidentMap();
    }
    
    public void showIncidentReports() {
    	mappingUIController.showIncidentReports();
    }
    
    public void refreshIncidentReports() {
    	mappingUIController.refreshIncidentReports();
    }
    
    public void beginSynchronization() {
    	mappingUIController.beginSynchronization();
    }
    
    public void refreshContacts() {
    	mappingUIController.refreshContacts();
    }
}

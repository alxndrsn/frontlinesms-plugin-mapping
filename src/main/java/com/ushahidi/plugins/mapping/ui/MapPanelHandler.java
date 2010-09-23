package com.ushahidi.plugins.mapping.ui;

import java.io.File;

import thinlet.Thinlet;

import com.ushahidi.plugins.mapping.MappingPluginController;
import com.ushahidi.plugins.mapping.data.domain.Category;
import com.ushahidi.plugins.mapping.data.domain.Incident;
import com.ushahidi.plugins.mapping.data.domain.LocationDetails;
import com.ushahidi.plugins.mapping.data.domain.MappingSetup;
import com.ushahidi.plugins.mapping.data.repository.CategoryDao;
import com.ushahidi.plugins.mapping.data.repository.IncidentDao;
import com.ushahidi.plugins.mapping.data.repository.MappingSetupDao;
import com.ushahidi.plugins.mapping.ui.markers.FormMarker;
import com.ushahidi.plugins.mapping.ui.markers.IncidentMarker;
import com.ushahidi.plugins.mapping.ui.markers.Marker;
import com.ushahidi.plugins.mapping.ui.markers.MessageMarker;
import com.ushahidi.plugins.mapping.ui.markers.SurveyMarker;
import com.ushahidi.plugins.mapping.util.MappingLogger;
import com.ushahidi.plugins.mapping.util.MappingMessages;
import com.ushahidi.plugins.mapping.util.MappingProperties;

import net.frontlinesms.FrontlineSMS;
import net.frontlinesms.data.DuplicateKeyException;
import net.frontlinesms.data.domain.Contact;
import net.frontlinesms.data.domain.FrontlineMessage;
import net.frontlinesms.data.domain.FrontlineMessage.Status;
import net.frontlinesms.data.domain.FrontlineMessage.Type;
import net.frontlinesms.data.repository.ContactDao;
import net.frontlinesms.data.repository.MessageDao;
import net.frontlinesms.plugins.forms.data.domain.FormResponse;
import net.frontlinesms.plugins.forms.data.repository.FormResponseDao;
import net.frontlinesms.plugins.surveys.data.domain.SurveyResponse;
import net.frontlinesms.plugins.surveys.data.repository.SurveyResponseDao;
import net.frontlinesms.ui.ExtendedThinlet;
import net.frontlinesms.ui.ThinletUiEventHandler;
import net.frontlinesms.ui.UiGeneratorController;

@SuppressWarnings("serial")
public class MapPanelHandler extends ExtendedThinlet implements ThinletUiEventHandler, MapListener {

	private static final String UI_PANEL_XML = "/ui/plugins/mapping/mapPanel.xml";
	
	private static MappingLogger LOG = MappingLogger.getLogger(MapPanelHandler.class);	
	
	private final MappingPluginController pluginController;
	private final FrontlineSMS frontlineController;
	private final UiGeneratorController ui;
	
	private final Object mainPanel;
	
	private final IncidentDao incidentDao;
	private final CategoryDao categoryDao;
	private final MessageDao messageDao;
	private final ContactDao contactDao;
	private SurveyResponseDao surveyResponseDao;
	private FormResponseDao formResponseDao;
	private final MappingSetupDao mappingSetupDao;
	
	private final MapBean mapBean;
	private final Object lblCoordinates;
	private final Object sldZoomLevel;
	private final Object cbxCategories;
	private final Object cbxShowMessages;
	private final Object cbxShowForms;
	private final Object cbxShowSurveys;
	private final Object cbxShowIncidents;
	
	public MapPanelHandler(MappingPluginController pluginController, FrontlineSMS frontlineController, UiGeneratorController uiController) {
		this.pluginController = pluginController;
		this.ui = uiController;
		this.frontlineController = frontlineController;
		
		this.incidentDao = pluginController.getIncidentDao();
		this.categoryDao = pluginController.getCategoryDao();
		this.messageDao = pluginController.getMessageDao();
		this.contactDao = pluginController.getContactDao();
		this.mappingSetupDao = pluginController.getMappingSetupDao();
		
		this.mainPanel = this.ui.loadComponentFromFile(UI_PANEL_XML, this);
		this.mapBean = (MapBean)get(this.find(this.mainPanel, "mapBean"), BEAN);
		this.lblCoordinates = this.ui.find(this.mainPanel, "lblCoordinates");
		this.sldZoomLevel = this.ui.find(this.mainPanel, "sldZoomLevel");
		this.cbxCategories = this.ui.find(this.mainPanel, "cbxCategories");
		this.cbxShowMessages = this.ui.find(this.mainPanel, "cbxShowMessages");
		this.cbxShowForms = this.ui.find(this.mainPanel, "cbxShowForms");
		this.cbxShowSurveys = this.ui.find(this.mainPanel, "cbxShowSurveys");
		this.cbxShowIncidents = this.ui.find(this.mainPanel, "cbxShowIncidents");
	}
	
	public Object getMainPanel() {
		return this.mainPanel;
	}
	
	public void init() {
		ui.removeAll(cbxCategories);
		mapBean.setMapProvider(MappingProperties.getDefaultMapProvider());
		if(mappingSetupDao.getDefaultSetup() != null) {
			MappingSetup defaultSetup = mappingSetupDao.getDefaultSetup();
			double latitude = defaultSetup.getLatitude();
			double longitude = defaultSetup.getLongitude();
			LOG.debug("Default Setup: " + defaultSetup.getSourceURL());
			if(mappingSetupDao.getDefaultSetup().isOffline()){				
				String fileName = defaultSetup.getOfflineMapFile();
				File file = new File(fileName);
				if (file.exists()) {
					mapBean.setOfflineMapFile(fileName);
				}
				else {
					defaultSetup.setOffline(false);
					defaultSetup.setOfflineMapFile(null);
					try{
						mappingSetupDao.updateMappingSetup(defaultSetup);
					}
					catch(DuplicateKeyException de) {
						LOG.debug(de);
						ui.alert("Unable to update the map setup");
					}					
				}
			}
			mapBean.setLocationAndZoomLevel(longitude, latitude, MappingProperties.getDefaultZoomLevel());			
			mapBean.clearMarkers(false);
			for(Incident incident : incidentDao.getAllIncidents(mappingSetupDao.getDefaultSetup())) {
				mapBean.addMarker(new IncidentMarker(incident), false);
			}
			mapBean.addMapListener(this);
			ui.setEnabled(sldZoomLevel, true);
			ui.add(cbxCategories, createComboboxChoice(MappingMessages.getAllCategories(), null));
			for(Category category : categoryDao.getAllCategories(mappingSetupDao.getDefaultSetup())){
				LOG.debug("Loading category %s", category.getTitle());
				ui.add(cbxCategories, createComboboxChoice(category.getTitle(), category));
			}
			ui.setSelectedIndex(cbxCategories, 0);
			ui.setEnabled(cbxCategories, true);
			if (getBoolean(cbxShowIncidents, Thinlet.ENABLED) == false) {
				ui.setSelected(cbxShowIncidents, true);
			}
			ui.setEnabled(cbxShowIncidents, true);
		} 
		else {
			double latitude = MappingProperties.getDefaultLatitude();
			double longitude = MappingProperties.getDefaultLongitude();
			mapBean.setLocationAndZoomLevel(longitude, latitude, MappingProperties.getDefaultZoomLevel());			
			mapBean.addMapListener(this);
			ui.setEnabled(sldZoomLevel, true);
			ui.setEnabled(cbxCategories, false);
			ui.setEnabled(cbxShowIncidents, false);
			ui.setSelected(cbxShowIncidents, false);
		}
		ui.setInteger(sldZoomLevel, VALUE, MappingProperties.getDefaultZoomLevel());
	}
	
	/**
	 * Refresh map and markers
	 */
	public void refresh() {
		LOG.debug("MapPanelHandler.refresh");
		if(mappingSetupDao.getDefaultSetup() != null) {
			if (getBoolean(cbxShowIncidents, Thinlet.ENABLED) == false) {
				ui.setSelected(cbxShowIncidents, true);
			}
			ui.setEnabled(cbxShowIncidents, true);
		}
		else {
			ui.setEnabled(cbxShowIncidents, false);
			ui.setSelected(cbxShowIncidents, false);
		}
		if (mapBean != null) {
			mapBean.clearMarkers(false);
			if(getBoolean(cbxShowMessages, Thinlet.SELECTED) && contactDao != null) {
				LOG.debug("Showing Messages");
				for(FrontlineMessage message : messageDao.getMessages(Type.RECEIVED, Status.RECEIVED)) {
					Contact contact = contactDao.getFromMsisdn(message.getSenderMsisdn());
					if (contact != null) {
						LocationDetails locationDetails = contact.getDetails(LocationDetails.class);
						if (locationDetails != null && locationDetails.getLocation() != null) {
							mapBean.addMarker(new MessageMarker(message, locationDetails.getLocation()), false);
						}
					}
				}
			}
			if(getBoolean(cbxShowSurveys, Thinlet.SELECTED) && surveyResponseDao != null) {
				LOG.debug("Showing Surveys");	
				for(SurveyResponse surveyResponse : surveyResponseDao.getAllSurveyResponses()) {
					Contact contact = surveyResponse.getContact();
					if (contact != null) {
						LocationDetails locationDetails = contact.getDetails(LocationDetails.class);
						if (locationDetails != null && locationDetails.getLocation() != null) {
							mapBean.addMarker(new SurveyMarker(surveyResponse, locationDetails.getLocation()), false);
						}
					}
				}		
			}
			if(getBoolean(cbxShowForms, Thinlet.SELECTED) && formResponseDao != null) {
				LOG.debug("Showing Forms");
				for(FormResponse formResponse : formResponseDao.getAllFormResponses()) {
					Contact contact = contactDao.getFromMsisdn(formResponse.getSubmitter());
					if (contact != null) {
						LocationDetails locationDetails = contact.getDetails(LocationDetails.class);
						if (locationDetails != null && locationDetails.getLocation() != null) {
							mapBean.addMarker(new FormMarker(formResponse, locationDetails.getLocation(), formResponse.getParentForm()), false);
						}
					}
				}
			}
			if(getBoolean(cbxShowIncidents, Thinlet.SELECTED) && incidentDao != null) {
				LOG.debug("Showing Incidents");
				for(Incident incident : incidentDao.getAllIncidents(mappingSetupDao.getDefaultSetup())) {
					mapBean.addMarker(new IncidentMarker(incident), false);
				}	
			}
			mapBean.repaint();
		}
	}
	
	public void setSurveyResponseDao(SurveyResponseDao surveyResponseDao) {
		this.surveyResponseDao = surveyResponseDao;
	}
	
	public void setFormResponseDao(FormResponseDao formResponseDao) {
		this.formResponseDao = formResponseDao;
	}
	
	public void destroyMap() {
		if (mapBean != null) {
			mapBean.destroyMap();
		}
	}
	
	public void addMapListener(MapListener listener) {
		mapBean.addMapListener(listener);
	}
	
	public void search(Object comboBox) {
		Object selectedItem =  getSelectedItem(comboBox);
		Category category = selectedItem != null ? getAttachedObject(selectedItem, Category.class) : null;
		LOG.debug("category=%s", category);
		mapBean.clearMarkers(false);
		for(Incident incident: incidentDao.getAllIncidents(mappingSetupDao.getDefaultSetup())){
			if (category == null || incident.hasCategory(category)) {
				mapBean.addMarker(new IncidentMarker(incident), false);
			}
		}
		mapBean.repaint();
	}
	
	/** @see {@link MapListener#mapZoomed(int)} */
	public void zoomChanged(int zoom){
	    LOG.info("Updating zoom controller to level " + zoom);
	    ui.setInteger(sldZoomLevel, VALUE, zoom);
	}
	
	/**
	 * Changes the zoom level of the map
	 * 
	 * @param zoomController The Zoom UI control
	 */
	public void zoomChanged(Object zoomController){
		int currentZoom = mapBean.getZoomLevel();		
		int zoomValue = getInteger(zoomController, ExtendedThinlet.VALUE);
		if(currentZoom < zoomValue){
			ui.setInteger(zoomController, ExtendedThinlet.VALUE, zoomValue - 1);
		}
		else if (currentZoom > zoomValue){
			ui.setInteger(zoomController, ExtendedThinlet.VALUE, zoomValue + 1);
		}
		mapBean.setZoomLevel(zoomValue);
	}
	
	/**
	 * Show the map save dialog
	 */
	public void saveMap() {
		MapSaveDialogHandler mapSaveDialog = new MapSaveDialogHandler(pluginController, frontlineController, ui);
		mapSaveDialog.showDialog(mapBean);
	}

	public void locationHovered(double latitude, double longitude) {
		String latitudeString = Double.toString(latitude);
		if (latitudeString.length() > 8) {
			latitudeString = latitudeString.substring(0,8);
		}
		String longitudeString = Double.toString(longitude);
		if (longitudeString.length() > 8) {
			longitudeString = longitudeString.substring(0,8);
		}
		ui.setText(lblCoordinates, String.format("%s, %s", latitudeString, longitudeString));
	}

	public void locationSelected(double latitude, double longitude) {}
	
	public void markerSelected(Marker marker) { 
		if (marker instanceof IncidentMarker) {
			IncidentMarker incidentMarker = (IncidentMarker)marker;
			if (incidentMarker.getIncident() != null) {
				LOG.debug("Incident: %s", incidentMarker.getIncident().getTitle());
				ReportDialogHandler reportDialog = new ReportDialogHandler(pluginController, frontlineController, ui);
				reportDialog.showDialog(incidentMarker.getIncident());
			}
		}
		else if (marker instanceof MessageMarker) {
			MessageMarker messageMarker = (MessageMarker)marker;
			if (messageMarker != null && messageMarker.getFrontlineMessage() != null) {
				LOG.debug("Message: %s", messageMarker.getFrontlineMessage().getTextContent());
				ResponseDialogHandler responseDialog = new ResponseDialogHandler(pluginController, frontlineController, ui);
				responseDialog.showDialog(messageMarker.getFrontlineMessage(), messageMarker.getLocation());
			}
		}
		else if (marker instanceof FormMarker) {
			FormMarker formMarker = (FormMarker)marker;
			if (formMarker != null && formMarker.getFormResponse() != null) {
				LOG.debug("Form: %s", formMarker.getFormResponse());
				ResponseDialogHandler responseDialog = new ResponseDialogHandler(pluginController, frontlineController, ui);
				responseDialog.setFormResponseDao(formResponseDao);
				responseDialog.showDialog(formMarker.getFormResponse(), formMarker.getLocation(), formMarker.getForm());
			}
		}
		else if (marker instanceof SurveyMarker) {
			SurveyMarker surveyMarker = (SurveyMarker)marker;
			if(surveyMarker != null && surveyMarker.getSurveyResponse() != null) {
				LOG.debug("Survey: %s", surveyMarker.getSurveyResponse());
				ResponseDialogHandler responseDialog = new ResponseDialogHandler(pluginController, frontlineController, ui);
				responseDialog.showDialog(surveyMarker.getSurveyResponse(), surveyMarker.getLocation());
			}
		}
	}
}
package agents;

import data.Journey;
import gui.AlertGui;
import gui.EnchereGui;
import jade.core.AID;
import jade.core.ServiceException;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.messaging.TopicManagementHelper;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAException;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionInitiator;

import java.awt.*;
import java.lang.reflect.Array;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Journey searcher
 * 
 * @author Emmanuel ADAM
 */
@SuppressWarnings("serial")
public class EnchereAgent extends GuiAgent {
	/** code pour ajout de livre par la gui */
	public static final int EXIT = 0;
	/** code pour achat de livre par la gui */
	public static final int ENCHERE_HOLLANDAISE = 1;

	/** topic on which the alert will be send */
	AID topic_ticketSell;
	AID topic_ticketBought;
	AID topic_ticketCurrentlySell;

	private ArrayList<AID> clients;
	private long endTime;
	private long startTime;

	/** gui */
	private EnchereGui window;

	ArrayList<String> ticketToSellList;
	boolean ticketSold;
	int currentPrice;

	/** Initialisation de l'agent */
	@Override
	protected void setup() {
		this.ticketToSellList = new ArrayList<>();
		ticketSold = false;
		this.window = new EnchereGui(this);
		window.setColor(Color.cyan);
		window.println("Hello!  Agent d'alertes " + this.getLocalName() + " est pret. ");
		window.setVisible(true);

		AgentToolsEA.register(this, "enchere agency", "enchere");
		detectClient();
		TopicManagementHelper topicHelper = null;
		try {
			topicHelper = (TopicManagementHelper) getHelper(TopicManagementHelper.SERVICE_NAME);
			topic_ticketSell = topicHelper.createTopic("TICKET SELL");
			topic_ticketBought = topicHelper.createTopic("TICKET BOUGHT");
			topic_ticketCurrentlySell = topicHelper.createTopic("TICKET CURRENTLY SELL");
			topicHelper.register(topic_ticketSell);
			topicHelper.register(topic_ticketBought);
			topicHelper.register(topic_ticketCurrentlySell);
		} catch (ServiceException e) {
			e.printStackTrace();
		}

		//REGLAGE ECOUTE DE LA RADIO
		topic_ticketSell = AgentToolsEA.generateTopicAID(this, "TICKET SELL");
		topic_ticketBought = AgentToolsEA.generateTopicAID(this, "TICKET BOUGHT");
		topic_ticketCurrentlySell = AgentToolsEA.generateTopicAID(this, "TICKET CURRENTLY SELL");
		//ecoute des messages radio
		addBehaviour(new CyclicBehaviour() {
			@Override
			public void action() {
				var msg_ticketSell = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketSell));
				var msg_ticketBought = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketBought));
				var msg_ticketCurrentlySell = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketCurrentlySell));
				if (msg_ticketSell != null) {
					println("Message recu sur le topic " + topic_ticketSell.getLocalName() + ". Contenu " + msg_ticketSell.getContent()
							+ " émis par " + msg_ticketSell.getSender().getLocalName());
					if(topic_ticketSell.getLocalName().toLowerCase().equals("ticket sell")) {
						println("BIEN UNE MISE EN ENCHERE DE TICKET");
						var start = msg_ticketSell.getUserDefinedParameter("start");
						var arrival = msg_ticketSell.getUserDefinedParameter("arrival");
						var departureDate = msg_ticketSell.getUserDefinedParameter("departureDate");
						var arrivalDate = msg_ticketSell.getUserDefinedParameter("arrivalDate");

						if (start != null && arrival != null & departureDate != null & arrivalDate != null) {
							String journeyInfo = "start:" + start + " arrival:" + arrival + " departureDate:" + departureDate + " arrivalDate:" + arrivalDate;
							System.out.println(journeyInfo);
							ticketToSellList.add(journeyInfo);
							System.out.println("nb ticket to sell : " + ticketToSellList.size());
							ticketSold = false;
						}
					}
				}
				if (msg_ticketBought != null) {
					if(topic_ticketBought.getLocalName().toLowerCase().equals("ticket bought")) {
						println("BIEN UN ACHAT TICKET");
						ticketSold = true;
						ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
						alert.addReceiver(topic_ticketCurrentlySell);
						alert.addUserDefinedParameter("currentPrice", Integer.toString(currentPrice));
						send(alert);
						msg_ticketBought = null;
					}
				}
				if (msg_ticketCurrentlySell != null) {
					if(topic_ticketCurrentlySell.getLocalName().toLowerCase().equals("ticket currently sell") && currentPrice != 0) {
						ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
						if (currentPrice == 1) {
							alert.addReceiver(topic_ticketBought);
							window.println("Arret de la vente, aucun acheteur");
							alert.setContent("Aucun acheteur, arret de la vente");
							ticketToSellList.remove(0);
							currentPrice = 0;
							send(alert);
						}
						else {
							alert.addReceiver(topic_ticketCurrentlySell);
							System.out.println("parameter : "+msg_ticketCurrentlySell.getUserDefinedParameter("currentPrice"));
							if (msg_ticketCurrentlySell.getUserDefinedParameter("currentPrice") != null) {
								currentPrice = Integer.parseInt(msg_ticketCurrentlySell.getUserDefinedParameter("currentPrice"));
								System.out.println("currentPrice : "+currentPrice);
							}
							if (!ticketSold) {
								currentPrice--;
								alert.setContent("Price : " + currentPrice);
								window.println("Price : " + currentPrice);
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								alert.addUserDefinedParameter("currentPrice", Integer.toString(currentPrice));
								send(alert);
							}
							else {
								alert.addReceiver(topic_ticketBought);
								alert.removeReceiver(topic_ticketCurrentlySell);
								alert.setContent("Ticket achete pour :"+currentPrice+"\narret de la vente");
								window.println("Ticket achete pour :"+currentPrice+"\narret de la vente");
								if (ticketToSellList.size() > 0) {
									ticketToSellList.remove(0);
									send(alert);
								}
							}
						}

					}
				}
				else {
					block();
				}
			}
		});
		//FIN REGLAGE ECOUTE DE LA RADIO
	}

	// 'Nettoyage' de l'agent
	@Override
	protected void takeDown() {
		window.println("Je quitte la plateforme. ");
	}

	///// SETTERS AND GETTERS
	/**
	 * @return agent gui
	 */
	public EnchereGui getWindow() {
		return window;
	}

	/** get event from the GUI */
	@Override
	protected void onGuiEvent(final GuiEvent eventFromGui) {
		if (eventFromGui.getType() == EnchereAgent.EXIT) {
			doDelete();
		}
		if (eventFromGui.getType() == EnchereAgent.ENCHERE_HOLLANDAISE) {

			if (ticketToSellList.size()>0) {
				System.out.println("Vente au enchere.");
				ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
				alert.setContent("""
						Start enchere : Hollandaise
						- Cliquer sur Bet lorsque vous voulez acheter le ticket au prix indiqué
						- Le premier à valider le Bet obtient le ticket."""
						.concat("\nObjet de la vente : "+ticketToSellList.get(0)));
				alert.addReceiver(topic_ticketCurrentlySell);
				currentPrice = 10;
				alert.addUserDefinedParameter("currentPrice", "10");
				// alert.addUserDefinedParameter("basePrice", (String) eventFromGui.getParameter(0));
				//Integer.parseInt((String) eventFromGui.getParameter(0));
				send(alert);
			}
			else {
				window.println("Aucun ticket a vendre...");

			}
		}
	}

	/**ecoute des evenement de type enregistrement en tant qu'agence aupres des pages jaunes*/
	private void detectClient() {
		var model = AgentToolsEA.createAgentDescription("client", "client");
		var msg = DFService.createSubscriptionMessage(this, getDefaultDF(), model, null);
		clients = new ArrayList<>();
		addBehaviour(new SubscriptionInitiator(this, msg) {
			@Override
			protected void handleInform(ACLMessage inform) {
				window.println("Agent client " + getLocalName() + ": information recues de DF");
				try {
					var results = DFService.decodeNotification(inform.getContent());
					if (results.length > 0) {
						for (DFAgentDescription dfd : results) {
							clients.add(dfd.getName());
							window.println(dfd.getName().getName() + " s'est inscrit en tant que client");
						}
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
			}
		});
	}

	/**
	 * print a message on the window lined to the agent
	 * 
	 * @param msg
	 *            text to display in th window
	 */
	public void println(final String msg) {
		window.println(msg);
	}


}

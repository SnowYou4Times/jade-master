package agents;

import java.awt.Color;
import java.util.*;
import java.util.stream.Stream;

import comportements.BetForTicket;
import comportements.ContractNetAchat;
import data.ComposedJourney;
import data.Journey;
import data.JourneysList;
import gui.TravellerGui;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SubscriptionInitiator;

/**
 * Journey searcher
 * 
 * @author Emmanuel ADAM
 */
@SuppressWarnings("serial")
public class TravellerAgent extends GuiAgent {
	/** code pour ajout de livre par la gui */
	public static final int EXIT = 0;
	/** code pour achat de livre par la gui */
	public static final int BUY_TRAVEL = 1;

	public static final int BET_TICKET = 2;
	public static final int TICKET_SELL = 3;

	/** liste des vendeurs */
	private ArrayList<AID> vendeurs;
	private ArrayList<AID> enchere;

	private boolean betCurrentlyRunning;

	/** catalog received by the sellers */
	private JourneysList catalogs;

	/** the journey chosen by the agent*/
	private ComposedJourney myJourney;

	/** preference chosen by the traveller*/
	private String preference;

	/** topic from which the alert will be received */
	private AID topic_traffic;
	private AID topic_ticketSell;
	private AID topic_ticketBought;
	private AID topic_ticketCurrentlySell;
	private AID topic_ticketBet;
	/** gui */
	private TravellerGui window;

	/** Initialisation de l'agent */
	@Override
	protected void setup() {
		this.window = new TravellerGui(this);
		window.setColor(Color.cyan);
		window.println("Hello! AgentAcheteurCN " + this.getLocalName() + " est pret. ");
		window.setVisible(true);

		vendeurs = new ArrayList<>();
		betCurrentlyRunning = false;
		detectAgences();
		detectEnchere();

		AgentToolsEA.register(this, "client","client");

		topic_traffic = AgentToolsEA.generateTopicAID(this,"TRAFFIC NEWS");
		topic_ticketSell = AgentToolsEA.generateTopicAID(this,"TICKET SELL");
		topic_ticketBought = AgentToolsEA.generateTopicAID(this,"TICKET BOUGHT");
		topic_ticketCurrentlySell = AgentToolsEA.generateTopicAID(this, "TICKET CURRENTLY SELL");
		topic_ticketBet = AgentToolsEA.generateTopicAID(this, "TICKET BET");
		//ecoute des messages radio
		addBehaviour(new CyclicBehaviour() {
			@Override
			public void action() {
				var msg_traffic = myAgent.receive(MessageTemplate.MatchTopic(topic_traffic));
				var msg_ticketBought = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketBought));
				var msg_ticketCurrentlySell = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketCurrentlySell));
				var msg_ticketBet = myAgent.receive(MessageTemplate.MatchTopic(topic_ticketBet));
				if (msg_traffic != null) {
					println("Message recu sur le topic " + topic_traffic.getLocalName() + ". Contenu " + msg_traffic.getContent()
							+ " émis par " + msg_traffic.getSender().getLocalName());
					if(topic_traffic.getLocalName().toLowerCase().equals("traffic news")) {
						println("TRAFFIC NEWS, problème sur le trajet :");
						var start = msg_traffic.getUserDefinedParameter("start");
						var arrival = msg_traffic.getUserDefinedParameter("arrival");
						println("Point de départ : "+ start);
						println("Point d'arrivée : "+ arrival);
						if(myJourney != null) {
							println("Mon trajet complet actuel : \n"+myJourney.toString());
							/*journey from B to C, duration  10 mn, departure: 610, arrival:620, cost = 1.0
			--traject from B to C by bus, departure: 610, arrival:620, cost = 1.0, sit left = 50, proposed by agentBus

		sb.append(ComposedJourney.JOURNEYFROM).append(start).append(ComposedJourney.TO).append(stop).
				append(ComposedJourney.DURATION).append(duration).append(ComposedJourney.DEPARTURE).
				append(departureDate).append(ComposedJourney.ARRIVAL).append(arrivalDate).
				append(ComposedJourney.COST).append(cost).append(ComposedJourney.LINE);
		for (Journey j : journeys)
			sb.append(ComposedJourney.SEP).append(j).append(ComposedJourney.LINE);
		return sb.toString();*/
							boolean isJourneyAborted = false;
							for (Journey j : myJourney.getJourneys()) {
								if(j.getStart().toLowerCase().equals(start.toLowerCase()) && j.getStop().toLowerCase().equals(arrival.toLowerCase())) {
									// the journey can no longer be used, need to remove the travel
									println("Un trajet n'est plus disponible, suppression du trajet complet.");
									double random = Math.random();
									System.out.println("random : "+ random);
									boolean isTravelNull = false;
									if (random > 0.5) {
										println("Recherche d'un nouveau trajet...");
										computeComposedJourney(myJourney.getStart(), myJourney.getStop(), myJourney.getDepartureDate(), preference);
										if (myJourney == null) {
											isTravelNull = true;
										}
									}
									if (random <= 0.5 || isTravelNull) {
										println("Abandon de la recherche, vente des tickets.");
										myJourney = null;
										ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
										alert.setContent("Un ticket supplementaire a la mise en enchere");
										alert.addReceiver(topic_ticketSell);
										alert.addUserDefinedParameter("start", j.getStart());
										alert.addUserDefinedParameter("arrival", j.getStop());
										alert.addUserDefinedParameter("departureDate", Integer.toString(j.getDepartureDate()));
										alert.addUserDefinedParameter("arrivalDate", Integer.toString(j.getArrivalDate()));
										send(alert);
									}
									isJourneyAborted = true;
								}
							}
							if (!isJourneyAborted) {
								println("Pas d'insidence sur mon trajet complet !");
							}
						}
						else {
							println("Pas de trajet complet pour le moment.");
						}
					}
				}
				else if (msg_ticketCurrentlySell != null) {
					if(msg_ticketCurrentlySell.getContent() != null) {
						betCurrentlyRunning = true;
						window.println(msg_ticketCurrentlySell.getContent());
					}
				}
				else if (msg_ticketBet != null) {
					if(msg_ticketBet.getContent() != null) {
						betCurrentlyRunning = true;
						window.println(msg_ticketBet.getContent());
					}
				}
				else if (msg_ticketBought != null) {
					if (msg_ticketBought.getContent() != null) {
						window.println(msg_ticketBought.getContent());
					}
					betCurrentlyRunning = false;
					window.println("Plus de vente en cours");
				} else { block();}
			}
		});
		
	}

	/**ecoute des evenement de type enregistrement en tant qu'agence aupres des pages jaunes*/
	private void detectAgences()
	{
		var model = AgentToolsEA.createAgentDescription("travel agency", "seller");
		var msg = DFService.createSubscriptionMessage(this, getDefaultDF(), model, null);
		vendeurs = new ArrayList<>();
		addBehaviour(new SubscriptionInitiator(this, msg) {
			@Override
			protected void handleInform(ACLMessage inform) {
				window.println("Agent "+getLocalName()+": information recues de DF");
				try {
					var results = DFService.decodeNotification(inform.getContent());
					if (results.length > 0) {
						for (DFAgentDescription dfd:results) {
							vendeurs.add(dfd.getName());
							window.println(dfd.getName().getName() + " s'est inscrit en tant qu'agence");
						}
					}	
				}
				catch (FIPAException fe) {
					fe.printStackTrace();
				}
			}
		} );
	}

	/**ecoute des evenement de type enregistrement en tant qu'agence aupres des pages jaunes*/
	private void detectEnchere() {
		var model = AgentToolsEA.createAgentDescription("enchere agency", "enchere");
		var msg = DFService.createSubscriptionMessage(this, getDefaultDF(), model, null);
		enchere = new ArrayList<>();
		addBehaviour(new SubscriptionInitiator(this, msg) {
			@Override
			protected void handleInform(ACLMessage inform) {
				window.println("Agent enchere " + getLocalName() + ": information recues de DF");
				try {
					var results = DFService.decodeNotification(inform.getContent());
					if (results.length > 0) {
						for (DFAgentDescription dfd : results) {
							enchere.add(dfd.getName());
							window.println(dfd.getName().getName() + " s'est inscrit en tant qu'enchere");
						}
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
			}
		});
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
	public TravellerGui getWindow() {
		return window;
	}

	public void computeComposedJourney(final String from, final String to, final int departure,
			final String preference) {
		final List<ComposedJourney> journeys = new ArrayList<>();
		//recherche de trajets ac tps d'attentes entre via = 60mn
		final boolean result = catalogs.findIndirectJourney(from, to, departure, 60, new ArrayList<>(),
				new ArrayList<>(), journeys);

		if (!result) {
			println("no journey found !!!");
			myJourney = null;
		}
		if (result) {
			//oter les voyages demarrant trop tard (1h30 apres la date de depart souhaitee)
			journeys.removeIf(j->j.getJourneys().get(0).getDepartureDate()-departure>90);
			this.preference = preference;
			//TODO: replace below to make a compromise between cost and confort...
			switch (preference) {
				case "duration" -> {
					Stream<ComposedJourney> strCJ = journeys.stream();
					OptionalDouble moy = strCJ.mapToInt(ComposedJourney::getDuration).average();
					final double avg = moy.getAsDouble();
					println("duree moyenne = " + avg);//+ ", moy au carre = " + avg * avg);
					journeys.sort(Comparator.comparingInt(ComposedJourney::getDuration));
				}
				case "confort" -> journeys.sort(Comparator.comparingInt(ComposedJourney::getConfort).reversed());
				case "cost" -> journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
				case "duration-cost" -> journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
				default -> journeys.sort(Comparator.comparingDouble(ComposedJourney::getCost));
			}
			myJourney = journeys.get(0);
			println("I choose this journey : " + myJourney);
		}
	}

	/** get event from the GUI */
	@Override
	protected void onGuiEvent(final GuiEvent eventFromGui) {
		if (eventFromGui.getType() == TravellerAgent.EXIT) {
			doDelete();
		}
		if (eventFromGui.getType() == TravellerAgent.BUY_TRAVEL) {
			addBehaviour(new ContractNetAchat(this, new ACLMessage(ACLMessage.CFP),
					(String) eventFromGui.getParameter(0), (String) eventFromGui.getParameter(1),
					(Integer) eventFromGui.getParameter(2), (String) eventFromGui.getParameter(3)));
		}
		if (eventFromGui.getType() == TravellerAgent.BET_TICKET) {
			System.out.println("EVENT GUI : betCurrentlyRunning = "+betCurrentlyRunning);
			if (betCurrentlyRunning) {
				betForTheTicket((int) eventFromGui.getParameter(0));
			}
			else {
				window.println("Aucune enchere en cours..");
			}
		}
	}

	protected void betForTheTicket(int priceBet) {
		if (priceBet >= 0) {
			if (priceBet == 0) {
				window.println("Enchere Hollandaise : Bet pour le ticket");
				ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
				alert.setContent("Achat du ticket");
				alert.addReceiver(topic_ticketBought);
				send(alert);
			}
			else {
				window.println("Enchere Vikrey : Bet pour le ticket à hauteur de "+priceBet);
				ACLMessage alert = new ACLMessage(ACLMessage.INFORM);
				alert.setContent("Bid pour le ticket");
				alert.addUserDefinedParameter("agentName", window.getTitle());
				alert.addUserDefinedParameter("price", String.valueOf(priceBet));
				alert.addReceiver(topic_ticketBet);
				send(alert);
			}
		}
		else {
			window.println("Prix du Bet incorrect !!");
		}
	}

	/**
	 * @return the vendeurs
	 */
	public List<AID> getVendeurs() {
		return (ArrayList<AID>)vendeurs.clone();
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

	/** set the list of journeys */
	public void setCatalogs(final JourneysList catalogs) {
		this.catalogs = catalogs;
	}


	public ComposedJourney getMyJourney() {
		return myJourney;
	}

}

package de.ipbhalle.metfraglib.fragmenter;

import java.util.ArrayList;

import de.ipbhalle.metfraglib.FastBitArray;
import de.ipbhalle.metfraglib.additionals.NeutralLosses;
import de.ipbhalle.metfraglib.fragment.AbstractTopDownBitArrayFragment;
import de.ipbhalle.metfraglib.fragment.BitArrayNeutralLoss;
import de.ipbhalle.metfraglib.list.FragmentList;
import de.ipbhalle.metfraglib.precursor.AbstractTopDownBitArrayPrecursor;
import de.ipbhalle.metfraglib.precursor.BitArrayPrecursor;
import de.ipbhalle.metfraglib.settings.Settings;

public class TopDownNeutralLossFragmenter extends TopDownFragmenter {

	protected BitArrayNeutralLoss[] detectedNeutralLosses;
	protected java.util.ArrayList<Short> brokenBondToNeutralLossIndex = new java.util.ArrayList<Short>();
	protected java.util.ArrayList<Integer> neutralLossIndex = new java.util.ArrayList<Integer>();

	public TopDownNeutralLossFragmenter(Settings settings) throws Exception {
		super(settings);
		this.detectedNeutralLosses = new NeutralLosses()
				.getMatchingAtoms((AbstractTopDownBitArrayPrecursor) this.scoredCandidate.getPrecursorMolecule());
	}

	public void nullify() {
		super.nullify();
		this.detectedNeutralLosses = null;
		this.brokenBondToNeutralLossIndex = null;
		this.neutralLossIndex = null;
	}

	public FragmentList generateFragments() {
		FragmentList generatedFragments = new FragmentList();
		java.util.Queue<AbstractTopDownBitArrayFragment> temporaryFragments = new java.util.LinkedList<AbstractTopDownBitArrayFragment>();
		java.util.Queue<Byte> numberOfFragmentAddedToQueue = new java.util.LinkedList<Byte>();
		java.util.Queue<de.ipbhalle.metfraglib.FastBitArray> nextBondIndecesToRemove = new java.util.LinkedList<de.ipbhalle.metfraglib.FastBitArray>();

		/*
		 * set first fragment as root for fragment generation (precursor)
		 */
		AbstractTopDownBitArrayFragment root = ((AbstractTopDownBitArrayPrecursor) this.scoredCandidate
				.getPrecursorMolecule()).toFragment();
		root.setID(++this.numberOfGeneratedFragments);
		root.setWasRingCleavedFragment(false);
		generatedFragments.addElement(root);
		temporaryFragments.add(root);
		numberOfFragmentAddedToQueue.add((byte) 1);
		nextBondIndecesToRemove.add(root.getBondsFastBitArray());

		for (int k = 1; k <= this.maximumTreeDepth; k++) {
			java.util.Queue<AbstractTopDownBitArrayFragment> newTemporaryFragments = new java.util.LinkedList<AbstractTopDownBitArrayFragment>();
			java.util.Queue<Byte> newNumberOfFragmentAddedToQueue = new java.util.LinkedList<Byte>();
			java.util.Queue<de.ipbhalle.metfraglib.FastBitArray> newNextBondIndecesToRemove = new java.util.LinkedList<de.ipbhalle.metfraglib.FastBitArray>();

			while (!temporaryFragments.isEmpty()) {
				AbstractTopDownBitArrayFragment nextTopDownFragmentForFragmentation = temporaryFragments.poll();
				byte numberOfNextTopDownFragmentForFragmentationAddedToQueue = numberOfFragmentAddedToQueue.poll();
				int[] indecesOfSetBondsOfNextTopDownFragment = nextBondIndecesToRemove.poll().getSetIndeces();

				for (int i = 0; i < indecesOfSetBondsOfNextTopDownFragment.length; i++) {
					short nextBondIndexToRemove = (short) indecesOfSetBondsOfNextTopDownFragment[i];
					/*
					 * if index of selected bond is smaller than the maximum
					 * index of a cleaved bond of the fragment then select
					 * another bond prevents generating fragments redundantly
					 */
					if (nextBondIndexToRemove < nextTopDownFragmentForFragmentation.getMaximalIndexOfRemovedBond()
							|| !nextTopDownFragmentForFragmentation.getBondsFastBitArray().get(nextBondIndexToRemove)) {
						continue;
					}
					short[] indecesOfBondConnectedAtoms = ((AbstractTopDownBitArrayPrecursor) this.scoredCandidate
							.getPrecursorMolecule()).getConnectedAtomIndecesOfBondIndex(nextBondIndexToRemove);
					/*
					 * getting fragment generated by cleavage of the current
					 * bond "nextBondIndexToRemove"
					 */
					AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments = nextTopDownFragmentForFragmentation
							.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), nextBondIndexToRemove, indecesOfBondConnectedAtoms);

					/*
					 * if we got two fragments then save these as valid ones
					 */
					if (newGeneratedTopDownFragments.length == 2) {
						this.checkForNeutralLossesAdaptMolecularFormulas(newGeneratedTopDownFragments,
								nextBondIndexToRemove);
						if (newGeneratedTopDownFragments[0].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit
								- this.minimumMassDeviationForFragmentGeneration) {
							newGeneratedTopDownFragments[0].setID(++this.numberOfGeneratedFragments);
							generatedFragments.addElement(newGeneratedTopDownFragments[0]);
							newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							newNumberOfFragmentAddedToQueue.add((byte) 1);
							newTemporaryFragments.add(newGeneratedTopDownFragments[0]);
						}
						if (newGeneratedTopDownFragments[1].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit
								- this.minimumMassDeviationForFragmentGeneration) {
							newGeneratedTopDownFragments[1].setID(++this.numberOfGeneratedFragments);
							generatedFragments.addElement(newGeneratedTopDownFragments[1]);
							newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[1].getBondsFastBitArray());
							newNumberOfFragmentAddedToQueue.add((byte) 1);
							newTemporaryFragments.add(newGeneratedTopDownFragments[1]);
						}
					}
					/*
					 * if just one fragment then we have to cleave once again
					 */
					else {
						if (newGeneratedTopDownFragments[0].getMonoisotopicMass(this.scoredCandidate.getPrecursorMolecule()) > this.minimumFragmentMassLimit
								- this.minimumMassDeviationForFragmentGeneration) {
							if (numberOfNextTopDownFragmentForFragmentationAddedToQueue < this.maximumNumberOfAFragmentAddedToQueue) {
								temporaryFragments.add(newGeneratedTopDownFragments[0]);
								numberOfFragmentAddedToQueue
										.add((byte) (numberOfNextTopDownFragmentForFragmentationAddedToQueue + 1));
								// nextBondIndecesToRemove.add(this.precursorMolecule.getFastBitArrayOfBondsBelongingtoRingLikeBondIndex(nextBondIndexToRemove));
								nextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							} else {
								newTemporaryFragments.add(newGeneratedTopDownFragments[0]);
								newNumberOfFragmentAddedToQueue.add((byte) 1);
								newNextBondIndecesToRemove.add(newGeneratedTopDownFragments[0].getBondsFastBitArray());
							}
						}
					}
				}
			}
			temporaryFragments = newTemporaryFragments;
			numberOfFragmentAddedToQueue = newNumberOfFragmentAddedToQueue;
			nextBondIndecesToRemove = newNextBondIndecesToRemove;
		}

		temporaryFragments = null;
		numberOfFragmentAddedToQueue = null;
		nextBondIndecesToRemove = null;

		return generatedFragments;
	}

	/**
	 * return true if neutral loss has been detected before true is returned
	 * mass as well as molecular formula is modified
	 * 
	 * @param newGeneratedTopDownFragments
	 * @return
	 */
	private boolean checkForNeutralLossesAdaptMolecularFormulas(
			AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments, short removedBondIndex) {
		if (newGeneratedTopDownFragments.length != 2) {
			System.err.println("Error: Cannot check for neutral losses for these fragments.");
			return false;
		}
		byte neutralLossFragment = -1;
		for (int i = 0; i < this.detectedNeutralLosses.length; i++) {
			for (int ii = 0; ii < this.detectedNeutralLosses[i].getNumberNeutralLosses(); ii++) {
				if (newGeneratedTopDownFragments[0].getAtomsFastBitArray()
						.equals(this.detectedNeutralLosses[i].getNeutralLossAtomFastBitArray(ii))) {
					newGeneratedTopDownFragments[1].getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
							.setNumberHydrogens((short) (newGeneratedTopDownFragments[1]
									.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
									.getNumberHydrogens() + this.detectedNeutralLosses[i].getHydrogenDifference()));
					/*
					 * check for previous broken bonds caused by neutral loss
					 */
					int[] brokenBondIndeces = newGeneratedTopDownFragments[1].getBrokenBondIndeces();
					for (int s = 0; s < brokenBondIndeces.length; s++) {
						int index = this.brokenBondToNeutralLossIndex.indexOf((short) brokenBondIndeces[s]);
						if ((short) brokenBondIndeces[s] == removedBondIndex) {
							if (index == -1) {
								this.brokenBondToNeutralLossIndex.add(removedBondIndex);
								this.neutralLossIndex.add(i);
							}
							continue;
						}
						if (index != -1) {
							newGeneratedTopDownFragments[1]
									.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
									.setNumberHydrogens((short) (newGeneratedTopDownFragments[1]
											.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
											.getNumberHydrogens()
											+ this.detectedNeutralLosses[this.neutralLossIndex.get(index)]
													.getHydrogenDifference()));
						}
					}
					return true;
				} else if (newGeneratedTopDownFragments[1].getAtomsFastBitArray()
						.equals(this.detectedNeutralLosses[i].getNeutralLossAtomFastBitArray(ii))) {
					newGeneratedTopDownFragments[0].getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
							.setNumberHydrogens((short) (newGeneratedTopDownFragments[0]
									.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
									.getNumberHydrogens() + this.detectedNeutralLosses[i].getHydrogenDifference()));
					// newGeneratedTopDownFragments[0].setTreeDepth((byte)(newGeneratedTopDownFragments[0].getTreeDepth()
					// - 1));
					/*
					 * check for previous broken bonds caused by neutral loss
					 */
					int[] brokenBondIndeces = newGeneratedTopDownFragments[0].getBrokenBondIndeces();
					for (int s = 0; s < brokenBondIndeces.length; s++) {
						int index = this.brokenBondToNeutralLossIndex.indexOf((short) brokenBondIndeces[s]);
						if ((short) brokenBondIndeces[s] == removedBondIndex) {
							if (index == -1) {
								this.brokenBondToNeutralLossIndex.add(removedBondIndex);
								this.neutralLossIndex.add(i);
							}
							continue;
						}
						if (index != -1) {
							newGeneratedTopDownFragments[0]
									.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
									.setNumberHydrogens((short) (newGeneratedTopDownFragments[0]
											.getMolecularFormula(this.scoredCandidate.getPrecursorMolecule())
											.getNumberHydrogens()
											+ this.detectedNeutralLosses[this.neutralLossIndex.get(index)]
													.getHydrogenDifference()));
						}
					}
					return true;
				}
			}
		}
		if (neutralLossFragment == -1)
			return false;
		return true;
	}

	/**
	 * generates all fragments of the given precursor fragment to reach the new
	 * tree depth
	 */
	@Override
	public ArrayList<AbstractTopDownBitArrayFragment> getFragmentsOfNextTreeDepth(
			AbstractTopDownBitArrayFragment precursorFragment) {
		FastBitArray ringBonds = new FastBitArray(precursorFragment.getBondsFastBitArray().getSize(), false);
		java.util.Queue<AbstractTopDownBitArrayFragment> ringBondCuttedFragments = new java.util.LinkedList<AbstractTopDownBitArrayFragment>();
		java.util.Queue<Short> lastCuttedBondOfRing = new java.util.LinkedList<Short>();
		ArrayList<AbstractTopDownBitArrayFragment> fragmentsOfNextTreeDepth = new ArrayList<AbstractTopDownBitArrayFragment>();
		/*
		 * generate fragments of skipped bonds
		 */
		if (this.ringBondsInitialised)
			this.generateFragmentsOfSkippedBonds(fragmentsOfNextTreeDepth, precursorFragment);
		/*
		 * get the last bond index that was removed; from there on the next
		 * bonds will be removed
		 */
		short nextBrokenIndexBondIndexToRemove = (short) (precursorFragment.getMaximalIndexOfRemovedBond() + 1);
		/*
		 * start from the last broken bond index
		 */
		for (short i = nextBrokenIndexBondIndexToRemove; i < precursorFragment.getBondsFastBitArray().getSize(); i++) {
			if (!precursorFragment.getBondsFastBitArray().get(i))
				continue;
			short[] indecesOfBondConnectedAtoms = ((BitArrayPrecursor) this.scoredCandidate.getPrecursorMolecule())
					.getConnectedAtomIndecesOfBondIndex(i);
			/*
			 * try to generate at most two fragments by the removal of the given
			 * bond
			 */
			AbstractTopDownBitArrayFragment[] newGeneratedTopDownFragments = precursorFragment.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), i,
					indecesOfBondConnectedAtoms);
			/*
			 * in case the precursor wasn't splitted try to cleave an additional
			 * bond until
			 * 
			 * 1. two fragments are generated or 2. the maximum number of trials
			 * have been reached 3. no further bond can be removed
			 */
			if (newGeneratedTopDownFragments.length == 1) {
				ringBonds.set(i, true);
				newGeneratedTopDownFragments[0].setLastSkippedBond((short) (i + 1));
				ringBondCuttedFragments.add(newGeneratedTopDownFragments[0]);
				lastCuttedBondOfRing.add(i);
				if (!this.ringBondsInitialised)
					this.ringBondFastBitArray.set(i);
			}
			/*
			 * pre-processing of the generated fragment/s
			 */
			this.processGeneratedFragments(newGeneratedTopDownFragments);
			/*
			 * if two new fragments have been generated set them as valid
			 */
			if (newGeneratedTopDownFragments.length == 2) {
				this.checkForNeutralLossesAdaptMolecularFormulas(newGeneratedTopDownFragments, i);
				newGeneratedTopDownFragments[0].setAsValidFragment();
				newGeneratedTopDownFragments[1].setAsValidFragment();
			}
			/*
			 * add fragment/s to vector after setting the proper precursor
			 */
			for (int k = 0; k < newGeneratedTopDownFragments.length; k++) {
				// precursorFragment.addChild(newGeneratedTopDownFragments[k]);
				if (newGeneratedTopDownFragments.length == 2)
					fragmentsOfNextTreeDepth.add(newGeneratedTopDownFragments[k]);
				/*
				if (precursorFragment.isValidFragment()) {
					newGeneratedTopDownFragments[k].setPrecursorFragment(precursorFragment);
				} else {
					newGeneratedTopDownFragments[k].setPrecursorFragment(precursorFragment.hasPrecursorFragment()
							? precursorFragment.getPrecursorFragment() : precursorFragment);
				}*/

			}
		}
		/*
		 * create fragments by ring bond cleavage and store them in the given
		 * vector
		 */
		this.createRingBondCleavedFragments(fragmentsOfNextTreeDepth, precursorFragment, ringBondCuttedFragments,
				ringBonds, lastCuttedBondOfRing);
		this.ringBondsInitialised = true;

		return fragmentsOfNextTreeDepth;
	}

	/**
	 * 
	 */

	/*
	 * generate fragments by removing bonds that were skipped due to ring bond
	 * cleavage
	 */
	protected void generateFragmentsOfSkippedBonds(
			ArrayList<AbstractTopDownBitArrayFragment> newGeneratedTopDownFragments,
			AbstractTopDownBitArrayFragment precursorFragment) {
		short lastSkippedBonds = precursorFragment.getLastSkippedBond();
		short lastCuttedBond = (short) (precursorFragment.getMaximalIndexOfRemovedBond());
		if (lastSkippedBonds == -1)
			return;
		for (short currentBond = lastSkippedBonds; currentBond < lastCuttedBond; currentBond++) {

			if (this.ringBondFastBitArray.get(currentBond))
				continue;
			if (!precursorFragment.getBondsFastBitArray().get(currentBond))
				continue;

			short[] connectedAtomIndeces = ((BitArrayPrecursor) this.scoredCandidate.getPrecursorMolecule())
					.getConnectedAtomIndecesOfBondIndex((short) currentBond);

			AbstractTopDownBitArrayFragment[] newFragments = precursorFragment.traverseMolecule(this.scoredCandidate.getPrecursorMolecule(), (short) currentBond,
					connectedAtomIndeces);

			this.processGeneratedFragments(newFragments);
			if (newFragments.length == 2) {
				this.checkForNeutralLossesAdaptMolecularFormulas(newFragments, currentBond);
				newFragments[0].setAsValidFragment();
				newFragments[1].setAsValidFragment();
			} else {
				System.err.println("problem generating fragments");
				System.exit(1);
			}

			for (int k = 0; k < newFragments.length; k++) {
				// precursorFragment.addChild(newFragments[k]);
				/*
				if (precursorFragment.isValidFragment())
					newFragments[k].setPrecursorFragment(precursorFragment);
				else
					newFragments[k].setPrecursorFragment(precursorFragment.hasPrecursorFragment()
							? precursorFragment.getPrecursorFragment() : precursorFragment);
				*/
				if (newFragments.length == 2) {
					newGeneratedTopDownFragments.add(newFragments[k]);
				}
			}
		}
	}

}

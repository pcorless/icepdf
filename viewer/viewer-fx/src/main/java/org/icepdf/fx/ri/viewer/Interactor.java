package org.icepdf.fx.ri.viewer;

public class Interactor {

    private final ViewerModel model;
    private int changeCount = 0;
//    private DomainObject domainObject;
//    private Service service = new Service();

    public Interactor(ViewerModel model) {
        this.model = model;
        createModelBindings();
    }

    private void createModelBindings() {
//        model.bindProperty3(Bindings.createBooleanBinding(() -> !model.getProperty1().isEmpty(), model
//        .property1Property()));
    }

    public void updateModelAfterSave() {
//        model.setProperty1("");
//        model.setProperty2(domainObject.getSomeValue());
//        changeCount = 0;

    }

    public void saveData() {
//        domainObject = service.saveDataSomewhere(model.getProperty1() + " --> " + changeCount);
    }

    public void updateChangeCount() {
        changeCount++;
    }
}
